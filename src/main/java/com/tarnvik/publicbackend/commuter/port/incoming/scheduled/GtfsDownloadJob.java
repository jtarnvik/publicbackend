package com.tarnvik.publicbackend.commuter.port.incoming.scheduled;

import com.tarnvik.publicbackend.commuter.model.domain.dao.GtfsDownloadDao;
import com.tarnvik.publicbackend.commuter.exception.GtfsDownloadException;
import com.tarnvik.publicbackend.commuter.service.GtfsPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class GtfsDownloadJob {
  private final GtfsPipelineService gtfsPipelineService;
  private final GtfsDownloadDao gtfsDownloadDao;

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    log.info("Application ready — running GTFS pipeline");
    runPipeline();
  }

  @Scheduled(cron = "0 0 5 * * *")
  public void scheduledDownload() {
    log.info("Running scheduled GTFS pipeline at 05:00");
    runPipeline();
  }

  @Scheduled(cron = "0 0 0 * * *")
  public void cleanOldEntries() {
    gtfsDownloadDao.deleteByDateBefore(LocalDate.now().minusDays(30));
  }

  private void runPipeline() {
    try {
      gtfsPipelineService.runPipeline();
    } catch (GtfsDownloadException e) {
      log.error("GTFS pipeline stopped: {}", e.getMessage());
    }
  }
}
