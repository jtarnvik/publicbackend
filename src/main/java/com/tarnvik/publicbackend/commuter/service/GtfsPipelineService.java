package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.port.outgoing.rest.samtrafiken.SamtrafikenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GtfsPipelineService {
  private final GtfsDownloadService gtfsDownloadService;
  private final GtfsParseService gtfsParseService;
  private final GtfsAccessService gtfsAccessService;
  private final SamtrafikenProvider samtrafikenProvider;

  public void runPipeline() {
    gtfsDownloadService.recoverIfNeeded();
    gtfsDownloadService.downloadIfNeeded();
    gtfsDownloadService.unzipIfReady();
    gtfsParseService.parseIfReady();
    gtfsAccessService.rebuildDataset();
    verifyRealtimeFeed();
  }

  /**
   * Fetches vehicle positions once and throws the result away.
   * <p>
   * Two purposes: it keeps the realtime API exercised even on days when nobody opens the live traffic view,
   * and it verifies that the feed still answers us — credentials, quota and response format — at a moment
   * when the log is being read anyway, rather than leaving the first discovery of a problem to a user
   * staring at an empty view.
   * <p>
   * Failure is logged and swallowed: the static pipeline has already succeeded by this point, and the
   * realtime feed being down is no reason to call that into question.
   */
  private void verifyRealtimeFeed() {
    try {
      int vehicleCount = samtrafikenProvider.fetchVehiclePositions().size();
      log.info("Realtime feed verified: {} vehicle positions", vehicleCount);
    } catch (Exception e) {
      log.error("Realtime feed check FAILED after the static pipeline: {} — live traffic will not work until "
        + "this is resolved. Static data is unaffected.", e.getMessage(), e);
    }
  }
}
