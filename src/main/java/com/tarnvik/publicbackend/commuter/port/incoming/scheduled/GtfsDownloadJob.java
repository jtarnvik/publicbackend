package com.tarnvik.publicbackend.commuter.port.incoming.scheduled;

import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsDownloadLogRepository;
import com.tarnvik.publicbackend.commuter.service.GtfsDownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class GtfsDownloadJob {
  private final GtfsDownloadService gtfsDownloadService;
  private final GtfsDownloadLogRepository downloadLogRepository;

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    log.info("Application ready — checking if GTFS download is needed");
    gtfsDownloadService.downloadIfNeeded();
  }

  @Scheduled(cron = "0 0 5 * * *")
  public void scheduledDownload() {
    log.info("Running scheduled GTFS download at 05:00");
    gtfsDownloadService.downloadIfNeeded();
  }

  @Scheduled(cron = "0 0 0 * * *")
  @Transactional
  public void cleanOldEntries() {
    downloadLogRepository.deleteByDateBefore(LocalDate.now().minusDays(30));
  }
}
