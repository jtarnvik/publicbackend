package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.dao.GtfsDownloadDao;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadLog;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadStatus;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.pushover.PushoverProvider;
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
import java.util.Comparator;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Slf4j
public class GtfsDownloadService {
  private static final Path WORK_DIR = Path.of("/tmp/sl-gtfs-cache");
  private static final Path ZIP_PATH = WORK_DIR.resolve("sl.zip");
  private static final Path UNZIP_DIR = WORK_DIR.resolve("unzipped");
  private static final int LOCAL_MAX_DOWNLOADS_PER_30_DAYS = 15;

  private final GtfsDownloadDao gtfsDownloadDao;
  private final PushoverProvider pushoverProvider;
  private final Environment environment;
  private final String apiKey;
  private final String gtfsUrl;

  public GtfsDownloadService(
    GtfsDownloadDao gtfsDownloadDao,
    PushoverProvider pushoverProvider,
    Environment environment,
    @Value("${samtrafiken.api-key}") String apiKey,
    @Value("${samtrafiken.gtfs-url}") String gtfsUrl
  ) {
    this.gtfsDownloadDao = gtfsDownloadDao;
    this.pushoverProvider = pushoverProvider;
    this.environment = environment;
    this.apiKey = apiKey;
    this.gtfsUrl = gtfsUrl;
  }

  public void runPipeline() {
    downloadIfNeeded();
    unzipIfReady();
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
      deleteDir(WORK_DIR);
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

      gtfsDownloadDao.markDownloadDone(entry);
    } catch (Exception e) {
      throw handlePipelineFailure(entry, "download", e);
    }
  }

  public void unzipIfReady() {
    LocalDate today = LocalDate.now();
    Optional<GtfsDownloadLog> maybeEntry = gtfsDownloadDao.findByDate(today);

    if (maybeEntry.isEmpty() || maybeEntry.get().getStatus() != GtfsDownloadStatus.DOWNLOAD_DONE) {
      log.info("GTFS unzip skipped — today's status is not DOWNLOAD_DONE");
      return;
    }

    GtfsDownloadLog entry = maybeEntry.get();
    gtfsDownloadDao.markUnzipStart(entry);

    try {
      deleteDir(UNZIP_DIR);
      Files.createDirectories(UNZIP_DIR);

      log.info("Unzipping GTFS zip to {}", UNZIP_DIR);
      long start = System.currentTimeMillis();
      int fileCount = 0;

      try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(ZIP_PATH))) {
        ZipEntry zipEntry;
        while ((zipEntry = zip.getNextEntry()) != null) {
          Path target = UNZIP_DIR.resolve(zipEntry.getName());
          Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
          zip.closeEntry();
          fileCount++;
        }
      }

      long duration = System.currentTimeMillis() - start;
      log.info("GTFS zip extracted: {} files, duration={}ms", fileCount, duration);

      gtfsDownloadDao.markUnzipDone(entry);
    } catch (Exception e) {
      throw handlePipelineFailure(entry, "unzip", e);
    }
  }

  public void resetToDownloadDone(GtfsDownloadLog entry) {
    try {
      deleteDir(UNZIP_DIR);
    } catch (IOException e) {
      log.warn("Could not delete unzip dir during reset: {}", e.getMessage());
    }
    gtfsDownloadDao.resetToDownloadDone(entry);
    log.info("GTFS pipeline reset to DOWNLOAD_DONE for date {}", entry.getDate());
  }

  private GtfsDownloadException handlePipelineFailure(GtfsDownloadLog entry, String phase, Exception e) {
    log.error("GTFS {} failed: {}", phase, e.getMessage(), e);
    gtfsDownloadDao.updateFailed(entry, e.getMessage());
    pushoverProvider.sendGtfsPipelineErrorNotification(phase, e.getMessage());
    return new GtfsDownloadException("GTFS " + phase + " failed: " + e.getMessage(), e);
  }

  private void deleteDir(Path dir) throws IOException {
    if (Files.exists(dir)) {
      try (var paths = Files.walk(dir)) {
        paths.sorted(Comparator.reverseOrder())
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
