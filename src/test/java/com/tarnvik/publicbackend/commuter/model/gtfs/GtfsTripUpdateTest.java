package com.tarnvik.publicbackend.commuter.model.gtfs;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripUpdate.StopDelay;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shape these tests are built around is the one measured on 2026-08-20: a prefix of stops the vehicle
 * has passed, each carrying a real second-precision delay, then a suffix of stops still ahead, every one of
 * them carrying a zero that means "not measured" rather than "on time".
 *
 * <pre>
 *   seq 1  +127s   passed
 *   seq 2   +34s   passed
 *   seq 3   +61s   passed   &lt;- the answer for a vehicle between stops 3 and 4
 *   seq 4     0s   ahead    &lt;- the trap
 *   seq 5     0s   ahead
 * </pre>
 */
class GtfsTripUpdateTest {
  private static final GtfsTripUpdate UPDATE = new GtfsTripUpdate("t1", List.of(
    new StopDelay(1, 127),
    new StopDelay(2, 34),
    new StopDelay(3, 61),
    new StopDelay(4, 0),
    new StopDelay(5, 0)));

  @Test
  void theDelayIsTheLastPassedStopsAndNotTheOrigins() {
    assertThat(UPDATE.delayAtOrBefore(3)).contains(Duration.ofSeconds(61));
  }

  /** The one mistake that matters: a zero from a stop ahead reports a late vehicle as punctual. */
  @Test
  void aStopAheadOfTheVehicleIsNeverRead() {
    assertThat(UPDATE.delayAtOrBefore(2)).contains(Duration.ofSeconds(34));
  }

  @Test
  void aVehicleStillAtItsOriginReadsItsOwnDeparture() {
    assertThat(UPDATE.delayAtOrBefore(1)).contains(Duration.ofSeconds(127));
  }

  /** A vehicle whose whole trip is still ahead of it — nothing has been measured, so nothing is reported. */
  @Test
  void aTripWithNothingPassedYetReportsNothing() {
    GtfsTripUpdate allAhead = new GtfsTripUpdate("t2", List.of(new StopDelay(4, 0), new StopDelay(5, 0)));

    assertThat(allAhead.delayAtOrBefore(3)).isEmpty();
  }

  @Test
  void aTripTheFeedSaysNothingAboutReportsNothing() {
    assertThat(new GtfsTripUpdate("t3", List.of()).delayAtOrBefore(3)).isEmpty();
  }

  /** Rows are read by sequence number, not by position, so a feed that sends them out of order still works. */
  @Test
  void theLatestSequenceWinsWhateverOrderTheRowsArriveIn() {
    GtfsTripUpdate shuffled = new GtfsTripUpdate("t4", List.of(
      new StopDelay(3, 61), new StopDelay(1, 127), new StopDelay(2, 34)));

    assertThat(shuffled.delayAtOrBefore(3)).contains(Duration.ofSeconds(61));
  }

  /** Buses really do run early at timing points — 109 s at the extreme — so a negative must survive. */
  @Test
  void anEarlyVehicleKeepsItsNegativeDelay() {
    GtfsTripUpdate early = new GtfsTripUpdate("t5", List.of(new StopDelay(2, -109)));

    assertThat(early.delayAtOrBefore(2)).contains(Duration.ofSeconds(-109));
  }
}
