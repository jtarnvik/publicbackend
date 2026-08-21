package com.tarnvik.publicbackend.commuter.port.outgoing.rest.samtrafiken;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripUpdate;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripUpdate.StopDelay;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

@Component
@Slf4j
public class SamtrafikenProvider {
  private final String staticUrl;
  private final String staticApiKey;
  private final String realtimeUrl;
  private final String tripUpdatesUrl;
  private final String realtimeApiKey;

  public SamtrafikenProvider(
    @Value("${samtrafiken.gtfs-static-url}") String staticUrl,
    @Value("${samtrafiken.gtfs-static-api-key}") String staticApiKey,
    @Value("${samtrafiken.gtfs-realtime-url}") String realtimeUrl,
    @Value("${samtrafiken.gtfs-trip-updates-url}") String tripUpdatesUrl,
    @Value("${samtrafiken.gtfs-realtime-api-key}") String realtimeApiKey
  ) {
    this.staticUrl = staticUrl;
    this.staticApiKey = staticApiKey;
    this.realtimeUrl = realtimeUrl;
    this.tripUpdatesUrl = tripUpdatesUrl;
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
    FeedMessage feed = fetchFeed(realtimeUrl);
    List<GtfsVehiclePosition> positions = new ArrayList<>();
    for (FeedEntity entity : feed.getEntityList()) {
      if (entity.hasVehicle()) {
        positions.add(toVehiclePosition(entity.getVehicle()));
      }
    }
    // Trace: one line per poll cycle, so info would swamp the log while anyone has the live view open.
    log.trace("GTFS-RT feed: {} total entities, {} with vehicle positions",
      feed.getEntityCount(), positions.size());
    return positions;
  }

  /**
   * How late every reporting vehicle was at the platforms it has already passed, keyed by trip id.
   * <p>
   * A second file on the same key and the same quota as the positions feed, and a separate URL because
   * {@code samtrafiken.gtfs-realtime-url} names {@code VehiclePositions.pb} specifically.
   * <p>
   * Trips whose rows carry no delay at all are dropped rather than kept empty — a vehicle that has only
   * just left its origin has nothing to say yet, and an empty entry would look like a measured zero to
   * anyone who forgot to check. Roughly one trip in ten is in that state at any moment.
   */
  public Map<String, GtfsTripUpdate> fetchTripUpdates() throws IOException {
    FeedMessage feed = fetchFeed(tripUpdatesUrl);
    Map<String, GtfsTripUpdate> updates = new HashMap<>();
    for (FeedEntity entity : feed.getEntityList()) {
      if (!entity.hasTripUpdate()) {
        continue;
      }
      TripUpdate tripUpdate = entity.getTripUpdate();
      String tripId = tripUpdate.getTrip().getTripId();
      if (tripId.isEmpty()) {
        continue;
      }
      List<StopDelay> stopDelays = toStopDelays(tripUpdate);
      if (!stopDelays.isEmpty()) {
        updates.put(tripId, new GtfsTripUpdate(tripId, stopDelays));
      }
    }
    // Trace: one line per poll cycle, matching the positions line above.
    log.trace("GTFS-RT trip updates: {} total entities, {} trips with a measured delay",
      feed.getEntityCount(), updates.size());
    return updates;
  }

  /**
   * The rows of one trip update that carry a delay, in the order the feed sent them — which is
   * {@code stop_sequence} order. Departure is preferred over arrival where a row has both: it is the later
   * of the two and so the more recent measurement, and it is what the next stop's timing follows from.
   */
  private List<StopDelay> toStopDelays(TripUpdate tripUpdate) {
    List<StopDelay> stopDelays = new ArrayList<>();
    for (StopTimeUpdate stopTimeUpdate : tripUpdate.getStopTimeUpdateList()) {
      Integer delay = delayOf(stopTimeUpdate);
      if (delay != null) {
        stopDelays.add(new StopDelay(stopTimeUpdate.getStopSequence(), delay));
      }
    }
    return stopDelays;
  }

  /**
   * The delay on one row, or null where the feed sent none. {@code hasDelay()} rather than
   * {@code getDelay()}: protobuf answers 0 for an unset field, which is the one value that must not be
   * confused with a measurement.
   */
  private Integer delayOf(StopTimeUpdate stopTimeUpdate) {
    if (stopTimeUpdate.hasDeparture() && stopTimeUpdate.getDeparture().hasDelay()) {
      return stopTimeUpdate.getDeparture().getDelay();
    }
    if (stopTimeUpdate.hasArrival() && stopTimeUpdate.getArrival().hasDelay()) {
      return stopTimeUpdate.getArrival().getDelay();
    }
    return null;
  }

  /**
   * One realtime file, parsed. The {@code Accept-Encoding: gzip} header is required rather than polite —
   * the API answers 406 without it.
   */
  private FeedMessage fetchFeed(String feedUrl) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) URI.create(feedUrl + "?key=" + realtimeApiKey)
      .toURL().openConnection();
    connection.setRequestProperty("Accept-Encoding", "gzip");
    try (InputStream raw = connection.getInputStream()) {
      InputStream input = "gzip".equalsIgnoreCase(connection.getContentEncoding())
        ? new GZIPInputStream(raw)
        : raw;
      return FeedMessage.parseFrom(input);
    }
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
