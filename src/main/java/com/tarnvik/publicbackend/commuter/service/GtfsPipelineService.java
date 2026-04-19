package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.dao.GtfsDownloadDao;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadLog;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsRouteRepository;
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

  public void runPipeline() {
    gtfsDownloadService.downloadIfNeeded();
    gtfsDownloadService.unzipIfReady();
    gtfsParseService.parseIfReady();
    gtfsAccessService.rebuildDataset();
  }

  @Transactional
  public void resetToDownloadDone(GtfsDownloadLog entry) {
    gtfsDownloadService.clearUnzipDir();
    gtfsTripRepository.deleteAll();
    gtfsRouteRepository.deleteAll();
    gtfsDownloadDao.resetToDownloadDone(entry);
    log.info("GTFS pipeline reset to DOWNLOAD_DONE for date {}", entry.getDate());
  }
}
