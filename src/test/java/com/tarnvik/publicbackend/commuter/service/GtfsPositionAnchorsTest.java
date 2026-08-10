package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsVehiclePosition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The behaviour these pin down is the train feed's, reproduced in miniature: a vehicle repeats one
 * position for several polls while the timestamp attached to it moves on. Anchoring is what stops those
 * seconds being read as delay.
 */
class GtfsPositionAnchorsTest {
  private static final String TRIP = "14010000707917731";
  private static final Instant T0 = Instant.parse("2026-08-10T13:11:39Z");
  private static final Instant NOW = Instant.parse("2026-08-10T13:20:00Z");

  private static GtfsVehiclePosition at(double lat, double lng, Instant timestamp) {
    return GtfsVehiclePosition.builder()
      .tripId(TRIP).lat(lat).lng(lng).timestamp(timestamp.getEpochSecond()).build();
  }

  @Test
  void aFirstSightingIsAnchoredToItsOwnTimestamp() {
    GtfsPositionAnchors anchors = new GtfsPositionAnchors();
    GtfsVehiclePosition report = at(59.38385, 17.89774, T0);

    anchors.update(List.of(report));

    assertThat(anchors.observedAt(report, NOW)).isEqualTo(T0);
  }

  @Test
  void aRefreshedTimestampAtAnUnchangedPositionKeepsTheEarlierMoment() {
    GtfsPositionAnchors anchors = new GtfsPositionAnchors();
    anchors.update(List.of(at(59.38385, 17.89774, T0)));

    // The same coordinates, two minutes of stamps later — the train has not been reported as moving.
    GtfsVehiclePosition restamped = at(59.38385, 17.89774, T0.plusSeconds(117));
    anchors.update(List.of(restamped));

    assertThat(anchors.observedAt(restamped, NOW)).isEqualTo(T0);
  }

  @Test
  void movingReanchorsToTheFreshReport() {
    GtfsPositionAnchors anchors = new GtfsPositionAnchors();
    anchors.update(List.of(at(59.38385, 17.89774, T0)));

    GtfsVehiclePosition moved = at(59.39055, 17.88774, T0.plusSeconds(140));
    anchors.update(List.of(moved));

    assertThat(anchors.observedAt(moved, NOW)).isEqualTo(T0.plusSeconds(140));
  }

  @Test
  void aReportArrivingOutOfOrderCannotPushTheAnchorForward() {
    GtfsPositionAnchors anchors = new GtfsPositionAnchors();
    anchors.update(List.of(at(59.38385, 17.89774, T0)));

    GtfsVehiclePosition older = at(59.38385, 17.89774, T0.minusSeconds(20));
    anchors.update(List.of(older));

    assertThat(anchors.observedAt(older, NOW)).isEqualTo(T0.minusSeconds(20));
  }

  @Test
  void aPositionTheAnchorHasNotSeenFallsBackToTheReport() {
    GtfsPositionAnchors anchors = new GtfsPositionAnchors();
    anchors.update(List.of(at(59.38385, 17.89774, T0)));

    // A caller holding a newer snapshot than the anchors were last folded from.
    GtfsVehiclePosition ahead = at(59.39055, 17.88774, T0.plusSeconds(140));

    assertThat(anchors.observedAt(ahead, NOW)).isEqualTo(T0.plusSeconds(140));
  }

  @Test
  void aReportWithNoUsableTimestampFallsBackToNow() {
    GtfsPositionAnchors anchors = new GtfsPositionAnchors();
    GtfsVehiclePosition unstamped = GtfsVehiclePosition.builder()
      .tripId(TRIP).lat(59.38385).lng(17.89774).timestamp(0).build();

    anchors.update(List.of(unstamped));

    assertThat(anchors.size()).isZero();
    assertThat(anchors.observedAt(unstamped, NOW)).isEqualTo(NOW);
  }

  @Test
  void aTripMissingFromASnapshotIsForgotten() {
    GtfsPositionAnchors anchors = new GtfsPositionAnchors();
    anchors.update(List.of(at(59.38385, 17.89774, T0)));
    assertThat(anchors.size()).isEqualTo(1);

    anchors.update(List.of());

    assertThat(anchors.size()).isZero();
    GtfsVehiclePosition restamped = at(59.38385, 17.89774, T0.plusSeconds(117));
    assertThat(anchors.observedAt(restamped, NOW)).isEqualTo(T0.plusSeconds(117));
  }
}
