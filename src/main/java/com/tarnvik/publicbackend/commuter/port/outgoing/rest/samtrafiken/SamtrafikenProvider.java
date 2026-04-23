package com.tarnvik.publicbackend.commuter.port.outgoing.rest.samtrafiken;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsVehiclePosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

@Component
@Slf4j
public class SamtrafikenProvider {
  private final String staticUrl;
  private final String staticApiKey;
  private final String realtimeUrl;
  private final String realtimeApiKey;

  public SamtrafikenProvider(
    @Value("${samtrafiken.gtfs-static-url}") String staticUrl,
    @Value("${samtrafiken.gtfs-static-api-key}") String staticApiKey,
    @Value("${samtrafiken.gtfs-realtime-url}") String realtimeUrl,
    @Value("${samtrafiken.gtfs-realtime-api-key}") String realtimeApiKey
  ) {
    this.staticUrl = staticUrl;
    this.staticApiKey = staticApiKey;
    this.realtimeUrl = realtimeUrl;
    this.realtimeApiKey = realtimeApiKey;
  }

  public void downloadGtfsZip(Path targetPath) throws IOException {
    String url = staticUrl + "?key=" + staticApiKey;
    log.info("Downloading GTFS zip from {}", staticUrl);
    long start = System.currentTimeMillis();

    HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
    connection.setRequestProperty("Accept-Encoding", "gzip");
    try (InputStream raw = connection.getInputStream()) {
      InputStream input = "gzip".equalsIgnoreCase(connection.getContentEncoding())
        ? new GZIPInputStream(raw)
        : raw;
      Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    long duration = System.currentTimeMillis() - start;
    long size = Files.size(targetPath);
    log.info("GTFS zip downloaded: size={} bytes, duration={}ms", size, duration);
  }

  public List<GtfsVehiclePosition> fetchVehiclePositions() throws IOException {
    String url = realtimeUrl + "?key=" + realtimeApiKey;

    HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
    connection.setRequestProperty("Accept-Encoding", "gzip");

    FeedMessage feed;
    try (InputStream raw = connection.getInputStream()) {
      InputStream input = "gzip".equalsIgnoreCase(connection.getContentEncoding())
        ? new GZIPInputStream(raw)
        : raw;
      feed = FeedMessage.parseFrom(input);
    }

    int totalEntities = feed.getEntityCount();
    List<GtfsVehiclePosition> positions = new ArrayList<>();

    for (FeedEntity entity : feed.getEntityList()) {
      if (entity.hasVehicle()) {
        positions.add(toVehiclePosition(entity.getVehicle()));
      }
    }

    log.info("GTFS-RT feed: {} total entities, {} with vehicle positions", totalEntities, positions.size());
    return positions;
  }

  private GtfsVehiclePosition toVehiclePosition(VehiclePosition vp) {
    return GtfsVehiclePosition.builder()
      .tripId(vp.getTrip().getTripId())
      .lat(vp.getPosition().getLatitude())
      .lng(vp.getPosition().getLongitude())
      .currentStatus(vp.getCurrentStatus())
      .timestamp(vp.getTimestamp())
      .bearing(vp.getPosition().getBearing())
      .speed(vp.getPosition().getSpeed())
      .directionId(vp.getTrip().getDirectionId())
      .currentStopSequence(vp.getCurrentStopSequence())
      .stopId(vp.getStopId())
      .routeId(vp.getTrip().getRouteId())
      .vehicleId(vp.getVehicle().getId())
      .vehicleLabel(vp.getVehicle().getLabel())
      .build();
  }
}
