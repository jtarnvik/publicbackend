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
   * Fetches each realtime feed once and throws the result away.
   * <p>
   * Two purposes: it keeps the realtime API exercised even on days when nobody opens the live traffic view,
   * and it verifies that the feeds still answer us — credentials, quota and response format — at a moment
   * when the log is being read anyway, rather than leaving the first discovery of a problem to a user
   * staring at an empty view.
   * <p>
   * <b>Both feeds are checked, and they are checked separately.</b> They are two files on one key, so a
   * credential or quota problem shows up in both, but a format or availability problem need not — and their
   * consequences differ enough to be worth telling apart: no positions means no live traffic at all, while
   * no trip updates means only that every delay falls back to the geometry. One failing must not stop the
   * other being checked, hence a check per feed rather than one try block over both.
   * <p>
   * Failure is logged and swallowed: the static pipeline has already succeeded by this point, and a realtime
   * feed being down is no reason to call that into question. That it is <em>only</em> logged is the gap goal
   * 2 step 6 exists to close.
   */
  private void verifyRealtimeFeed() {
    verifyFeed("VehiclePositions", () -> samtrafikenProvider.fetchVehiclePositions().size() + " positions",
      "live traffic will not work until this is resolved");
    verifyFeed("TripUpdates", () -> samtrafikenProvider.fetchTripUpdates().size() + " measured trips",
      "delays will fall back to the geometric estimate");
  }

  /** One feed, fetched and discarded. {@code consequence} says what the failure costs, in plain terms. */
  private void verifyFeed(String feed, ThrowingSupplier<String> fetch, String consequence) {
    try {
      log.info("Realtime feed {} verified: {}", feed, fetch.get());
    } catch (Exception e) {
      log.error("Realtime feed {} check FAILED after the static pipeline: {} — {}. Static data is "
        + "unaffected.", feed, e.getMessage(), consequence, e);
    }
  }

  /** A {@link java.util.function.Supplier} that may fail, which is the whole point of checking it. */
  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }
}
