package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.model.domain.dao.GtfsDownloadDao;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadLog;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadStatus;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.GtfsStatusResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.mapper.GtfsDownloadLogMapper;
import com.tarnvik.publicbackend.commuter.service.GtfsPipelineService;
import com.tarnvik.publicbackend.commuter.service.GtfsRealtimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
@Slf4j
public class GtfsAdminController {
  private final GtfsDownloadDao gtfsDownloadDao;
  private final GtfsPipelineService gtfsPipelineService;
  private final GtfsDownloadLogMapper gtfsDownloadLogMapper;
  private final GtfsRealtimeService gtfsRealtimeService;

  @GetMapping("/api/admin/gtfs/status")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<GtfsStatusResponse> getStatus() {
    Optional<GtfsDownloadLog> maybeEntry = gtfsDownloadDao.findMostRecent();
    if (maybeEntry.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(gtfsDownloadLogMapper.toResponse(maybeEntry.get()));
  }

  @PostMapping("/api/admin/gtfs/run-pipeline")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> runPipeline() {
    gtfsPipelineService.runPipeline();
    return ResponseEntity.ok().build();
  }

  @PostMapping("/api/admin/gtfs/realtime-poc")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> realtimePoc() {
    gtfsRealtimeService.poc();
    return ResponseEntity.ok().build();
  }

  @PostMapping("/api/admin/gtfs/reset")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> resetToDownloadDone() {
    Optional<GtfsDownloadLog> maybeEntry = gtfsDownloadDao.findMostRecent();
    if (maybeEntry.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    GtfsDownloadLog entry = maybeEntry.get();
    if (entry.getStatus() == GtfsDownloadStatus.DOWNLOAD_START
      || entry.getStatus() == GtfsDownloadStatus.DOWNLOAD_DONE) {
      return ResponseEntity.status(409).build();
    }
    gtfsPipelineService.resetToDownloadDone(entry);
    log.info("GTFS pipeline reset complete for date {}", entry.getDate());
    return ResponseEntity.ok().build();
  }
}
