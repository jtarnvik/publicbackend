package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.GtfsDownloadResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.GtfsFileInfo;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.GtfsFilesResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.GtfsUnzipResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/api/admin/gtfs-poc")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class GtfsPocController {
  private static final Path ZIP_PATH = Path.of("/tmp/sl.zip");
  private static final Path UNZIP_DIR = Path.of("/tmp/sl");

  private final String apiKey;
  private final String gtfsUrl;

  public GtfsPocController(
    @Value("${samtrafiken.gtfs-static-api-key}") String apiKey,
    @Value("${samtrafiken.gtfs-static-url}") String gtfsUrl
  ) {
    this.apiKey = apiKey;
    this.gtfsUrl = gtfsUrl;
  }

  @PostMapping("/download")
  public ResponseEntity<GtfsDownloadResponse> download() throws IOException {
    if (Files.exists(ZIP_PATH)) {
      long size = Files.size(ZIP_PATH);
      log.info("GTFS zip already exists at {}, size={} bytes — skipping download", ZIP_PATH, size);
      return ResponseEntity.ok(new GtfsDownloadResponse(true, size, null));
    }

    String url = gtfsUrl + "?key=" + apiKey;
    log.info("Downloading GTFS zip from {}", gtfsUrl);
    long start = System.currentTimeMillis();

    HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
    connection.setRequestProperty("Accept-Encoding", "gzip");
    try (InputStream raw = connection.getInputStream()) {
      InputStream input = "gzip".equalsIgnoreCase(connection.getContentEncoding())
        ? new GZIPInputStream(raw)
        : raw;
      Files.copy(input, ZIP_PATH, StandardCopyOption.REPLACE_EXISTING);
    }

    long duration = System.currentTimeMillis() - start;
    long size = Files.size(ZIP_PATH);
    log.info("GTFS zip downloaded: size={} bytes, duration={}ms", size, duration);
    return ResponseEntity.ok(new GtfsDownloadResponse(false, size, duration));
  }

  @PostMapping("/unzip")
  public ResponseEntity<GtfsUnzipResponse> unzip() throws IOException {
    Files.createDirectories(UNZIP_DIR);
    long start = System.currentTimeMillis();
    List<GtfsFileInfo> files = new ArrayList<>();

    try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(ZIP_PATH))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        Path target = UNZIP_DIR.resolve(entry.getName());
        Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
        files.add(new GtfsFileInfo(entry.getName(), Files.size(target)));
        zip.closeEntry();
      }
    }

    long duration = System.currentTimeMillis() - start;
    log.info("GTFS zip extracted: {} files, duration={}ms", files.size(), duration);
    return ResponseEntity.ok(new GtfsUnzipResponse(files, duration));
  }

  @GetMapping("/files")
  public ResponseEntity<GtfsFilesResponse> files() throws IOException {
    String pwd = "/Users/jesper/Downloads/sl - 2026-04-15 - 17.34";

    // Load all line 43 trip_ids into a set
    List<String> lines = Files.readAllLines(Paths.get(pwd + "/alltrips.txt"));
    Set<String> line43TripIds = new HashSet<>(lines);
    System.out.println("Line 43 trip_ids loaded: " + line43TripIds.size());

    // Parse VP feed
    FeedMessage feed;
    try (FileInputStream fis = new FileInputStream(pwd + "/VehiclePositions.pb")) {
      feed = FeedMessage.parseFrom(fis);
    }
    System.out.println("Total entities in feed: " + feed.getEntityCount());

    // Find matches
    int matchCount = 0;
    for (FeedEntity entity : feed.getEntityList()) {
      if (entity.hasVehicle()) {
        VehiclePosition vp = entity.getVehicle();
        String tripId = vp.getTrip().getTripId();
        if (line43TripIds.contains(tripId)) {
          matchCount++;
          System.out.println("--- Line 43 Vehicle " + matchCount + " ---");
          System.out.println("trip_id:   " + tripId);
          System.out.println("direction: " + vp.getTrip().getDirectionId());
          System.out.println("lat:       " + vp.getPosition().getLatitude());
          System.out.println("lon:       " + vp.getPosition().getLongitude());
          System.out.println("bearing:   " + vp.getPosition().getBearing());
          System.out.println("stop_seq:  " + vp.getCurrentStopSequence());
          System.out.println("stop_id:   " + vp.getStopId());
          System.out.println("status:    " + vp.getCurrentStatus());
          System.out.println("timestamp: " + vp.getTimestamp());
        }
      }
    }
    System.out.println("Total line 43 vehicles found: " + matchCount);

    return ResponseEntity.ok(new GtfsFilesResponse(List.of()));

//    if (!Files.exists(UNZIP_DIR)) {
//      return ResponseEntity.ok(new GtfsFilesResponse(List.of()));
//    }
//    List<GtfsFileInfo> files = Files.list(UNZIP_DIR)
//      .filter(p -> p.getFileName().toString().endsWith(".txt"))
//      .map(p -> {
//        try {
//          return new GtfsFileInfo(p.getFileName().toString(), Files.size(p));
//        } catch (IOException e) {
//          return new GtfsFileInfo(p.getFileName().toString(), -1L);
//        }
//      })
//      .sorted(Comparator.comparing(GtfsFileInfo::name))
//      .toList();
//    return ResponseEntity.ok(new GtfsFilesResponse(files));
  }
}
