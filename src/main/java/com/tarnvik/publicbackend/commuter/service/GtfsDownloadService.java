package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.dao.GtfsDownloadDao;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadLog;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.zip.GZIPInputStream;

@Service
@Slf4j
public class GtfsDownloadService {
  private static final Path WORK_DIR = Path.of("/tmp/sl-gtfs-cache");
  private static final Path ZIP_PATH = WORK_DIR.resolve("sl.zip");
  private static final int LOCAL_MAX_DOWNLOADS_PER_30_DAYS = 15;

  private final GtfsDownloadDao gtfsDownloadDao;
  private final Environment environment;
  private final String apiKey;
  private final String gtfsUrl;

  public GtfsDownloadService(
    GtfsDownloadDao gtfsDownloadDao,
    Environment environment,
    @Value("${samtrafiken.api-key}") String apiKey,
    @Value("${samtrafiken.gtfs-url}") String gtfsUrl
  ) {
    this.gtfsDownloadDao = gtfsDownloadDao;
    this.environment = environment;
    this.apiKey = apiKey;
    this.gtfsUrl = gtfsUrl;
  }

  public void downloadIfNeeded() {
    LocalDate today = LocalDate.now();

    if (gtfsDownloadDao.findByDate(today).isPresent()) {
      log.info("GTFS download already attempted today ({}), skipping", today);
      return;
    }

    if (environment.acceptsProfiles(Profiles.of("local"))) {
      long recentDownloads = gtfsDownloadDao.countByDateAfter(today.minusDays(30));
      if (recentDownloads > LOCAL_MAX_DOWNLOADS_PER_30_DAYS) {
        log.warn("GTFS download skipped: {} downloads in the last 30 days exceeds local limit of {}",
          recentDownloads, LOCAL_MAX_DOWNLOADS_PER_30_DAYS);
        return;
      }
    }

    GtfsDownloadLog entry = gtfsDownloadDao.insertDownloadStart(today);

    try {
      deleteWorkDir();
      Files.createDirectories(WORK_DIR);

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

      gtfsDownloadDao.updateStatus(entry, GtfsDownloadStatus.DOWNLOAD_DONE);
    } catch (Exception e) {
      log.error("GTFS download failed: {}", e.getMessage(), e);
      gtfsDownloadDao.updateFailed(entry, e.getMessage());
    }
  }

  private void deleteWorkDir() throws IOException {
    if (Files.exists(WORK_DIR)) {
      try (var paths = Files.walk(WORK_DIR)) {
        paths.sorted(java.util.Comparator.reverseOrder())
          .forEach(p -> {
            try {
              Files.delete(p);
            } catch (IOException e) {
              log.warn("Could not delete {}: {}", p, e.getMessage());
            }
          });
      }
    }
  }
}
