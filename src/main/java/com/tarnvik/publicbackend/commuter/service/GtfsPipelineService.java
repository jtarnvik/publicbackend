package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.dao.GtfsDownloadDao;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadLog;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsCalendarDateRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsRouteRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsStopRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsStopTimeRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsTripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GtfsPipelineService {
  private final GtfsDownloadService gtfsDownloadService;
  private final GtfsParseService gtfsParseService;
  private final GtfsAccessService gtfsAccessService;
  private final GtfsDownloadDao gtfsDownloadDao;
  private final GtfsRouteRepository gtfsRouteRepository;
  private final GtfsTripRepository gtfsTripRepository;
  private final GtfsStopTimeRepository gtfsStopTimeRepository;
  private final GtfsStopRepository gtfsStopRepository;
  private final GtfsCalendarDateRepository gtfsCalendarDateRepository;

  public void runPipeline() {
    gtfsDownloadService.recoverIfNeeded();
    gtfsDownloadService.downloadIfNeeded();
    gtfsDownloadService.unzipIfReady();
    gtfsParseService.parseIfReady();
    gtfsAccessService.rebuildDataset();
  }

  @Transactional
  public void resetToDownloadDone(GtfsDownloadLog entry) {
    log.info("Starting GTFS pipeline reset for date {}", entry.getDate());
    gtfsDownloadService.clearUnzipDir();
    gtfsStopTimeRepository.deleteAllInBatch();
    gtfsStopRepository.deleteAllInBatch();
    gtfsCalendarDateRepository.deleteAllInBatch();
    gtfsTripRepository.deleteAllInBatch();
    gtfsRouteRepository.deleteAllInBatch();
    gtfsDownloadDao.resetToDownloadDone(entry);
    log.info("GTFS pipeline reset queued — committing to database");
  }
}
