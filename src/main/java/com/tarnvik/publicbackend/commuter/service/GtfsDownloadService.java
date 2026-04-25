package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.exception.GtfsDownloadException;
import com.tarnvik.publicbackend.commuter.model.domain.dao.GtfsDownloadDao;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadLog;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadStatus;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.pushover.PushoverProvider;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.samtrafiken.SamtrafikenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;
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
  private final SamtrafikenProvider samtrafikenProvider;
  private final Environment environment;

  public GtfsDownloadService(
    GtfsDownloadDao gtfsDownloadDao,
    PushoverProvider pushoverProvider,
    SamtrafikenProvider samtrafikenProvider,
    Environment environment
  ) {
    this.gtfsDownloadDao = gtfsDownloadDao;
    this.pushoverProvider = pushoverProvider;
    this.samtrafikenProvider = samtrafikenProvider;
    this.environment = environment;
  }

  public void recoverIfNeeded() {
    LocalDate today = LocalDate.now();
    Optional<GtfsDownloadLog> maybeEntry = gtfsDownloadDao.findByDate(today);
    if (maybeEntry.isEmpty() || maybeEntry.get().getStatus() != GtfsDownloadStatus.PARSE_START) {
      return;
    }
    GtfsDownloadLog entry = maybeEntry.get();
    if (Files.exists(UNZIP_DIR.resolve("trips.txt"))) {
      log.warn("GTFS recovery: status was PARSE_START and unzipped files exist — resetting to UNZIP_DONE for re-parse");
      gtfsDownloadDao.resetToUnzipDone(entry);
    } else if (Files.exists(ZIP_PATH)) {
      log.warn("GTFS recovery: status was PARSE_START and zip exists — resetting to DOWNLOAD_DONE for re-unzip");
      gtfsDownloadDao.resetToDownloadDone(entry);
    } else {
      log.warn("GTFS recovery: status was PARSE_START and /tmp is empty — setting ERROR_IN_PARSE, live traffic unavailable today");
      gtfsDownloadDao.markErrorInParse(entry);
    }
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
      samtrafikenProvider.downloadGtfsZip(ZIP_PATH);
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

  public void clearUnzipDir() {
    try {
      deleteDir(UNZIP_DIR);
    } catch (IOException e) {
      log.warn("Could not delete unzip dir: {}", e.getMessage());
    }
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
