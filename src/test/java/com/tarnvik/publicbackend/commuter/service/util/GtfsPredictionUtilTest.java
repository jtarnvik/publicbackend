package com.tarnvik.publicbackend.commuter.service.util;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsVehiclePosition;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveTrip;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveVehicle;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.StopPrediction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Three stations on a straight north-south line at longitude 18.0, one every 0.001° of latitude ≈ 111 m,
 * laid out the same way as {@link GtfsGeometryUtilTest}.
 *
 * <pre>
 *   C  (59.002)  08:20
 *   |
 *   B  (59.001)  08:10
 *   |
 *   A  (59.000)  08:00
 * </pre>
 *
 * All times are Stockholm wall clock on a summer date, so 08:00 local is 06:00Z. The chain runs A→B→C in
 * direction 0, which makes a direction-0 trip the downward one.
 */
class GtfsPredictionUtilTest {
  private static final double LON = 18.000;
  private static final String STATION_A = "9021001000000001";
  private static final String STATION_B = "9021001000000002";
  private static final String STATION_C = "9021001000000003";

  private static final GtfsStopInfo A = platform(STATION_A, "Alfa", 59.000);
  private static final GtfsStopInfo B = platform(STATION_B, "Beta", 59.001);
  private static final GtfsStopInfo C = platform(STATION_C, "Gamma", 59.002);

  private static final Instant B_ON_TIME = Instant.parse("2026-08-06T06:10:00Z");
  private static final Instant C_ON_TIME = Instant.parse("2026-08-06T06:20:00Z");
  private static final Instant A_ON_TIME = Instant.parse("2026-08-06T06:00:00Z");

  private static GtfsStopInfo platform(String parentId, String name, double lat) {
    GtfsStopInfo parent = GtfsStopInfo.builder()
      .stopId(parentId).stopName(name).stopLat(lat).stopLon(LON).locationType(1).build();
    return GtfsStopInfo.builder()
      .stopId("9022" + parentId.substring(4)).stopName(name).stopLat(lat).stopLon(LON)
      .locationType(0).parentStation(parent).build();
  }

  private static GtfsStopTimeInfo stopTime(int sequence, GtfsStopInfo stop, String time, double distance) {
    return GtfsStopTimeInfo.builder()
      .stopSequence(sequence).stop(stop).arrivalTime(time).departureTime(time)
      .shapeDistTraveled(distance).stopHeadsign("Mot Gamma").build();
  }

  private static GtfsTripInfo trip(int directionId, List<GtfsStopTimeInfo> stopTimes) {
    return GtfsTripInfo.builder().tripId("t1").directionId(directionId).stopTimes(stopTimes).build();
  }

  /** A→B→C, calling at every station on time. Also the trip the chain itself is built from. */
  private static GtfsTripInfo downwardTrip() {
    return trip(0, List.of(
      stopTime(1, A, "08:00:00", 0.0),
      stopTime(2, B, "08:10:00", 111.0),
      stopTime(3, C, "08:20:00", 222.0)));
  }

  /** C→B→A, the same stations the other way about. */
  private static GtfsTripInfo upwardTrip() {
    return trip(1, List.of(
      stopTime(1, C, "08:00:00", 0.0),
      stopTime(2, B, "08:10:00", 111.0),
      stopTime(3, A, "08:20:00", 222.0)));
  }

  private static LiveVehicle vehicle(LiveTrip chain, GtfsTripInfo trip, double lat, double lon,
                                     Instant observedAt) {
    GtfsVehiclePosition position = GtfsVehiclePosition.builder()
      .tripId(trip.getTripId()).lat(lat).lng(lon).timestamp(observedAt.getEpochSecond()).build();
    return LiveVehicle.builder()
      .position(position)
      .location(GtfsGeometryUtil.locateOnRoute(chain.getLiveStops(), position))
      .trip(trip)
      .build();
  }

  private static LiveTrip chain() throws Exception {
    return new LiveTrip(downwardTrip(), List.of());
  }

  @Test
  void aPunctualVehiclePredictsItsTimetable() throws Exception {
    LiveTrip chain = chain();
    LiveVehicle atB = vehicle(chain, downwardTrip(), 59.001, LON, B_ON_TIME);

    List<StopPrediction> predictions = GtfsPredictionUtil.predict(chain, atB, Instant.now());

    assertThat(predictions).extracting(StopPrediction::stopId).containsExactly(STATION_B, STATION_C);
    assertThat(predictions).extracting(StopPrediction::departure).containsExactly(B_ON_TIME, C_ON_TIME);
  }

  @Test
  void aLateVehicleShiftsEveryRemainingStopByItsDelay() throws Exception {
    LiveTrip chain = chain();
    // At B, but reporting five minutes after it was due there.
    LiveVehicle atB = vehicle(chain, downwardTrip(), 59.001, LON, B_ON_TIME.plusSeconds(300));

    List<StopPrediction> predictions = GtfsPredictionUtil.predict(chain, atB, Instant.now());

    assertThat(predictions).extracting(StopPrediction::departure)
      .containsExactly(B_ON_TIME.plusSeconds(300), C_ON_TIME.plusSeconds(300));
  }

  @Test
  void anUpwardVehiclePredictsBackAlongTheChain() throws Exception {
    LiveTrip chain = chain();
    // Halfway between B and C, running the other way, on time for that point.
    LiveVehicle betweenCandB = vehicle(chain, upwardTrip(), 59.0015, LON, Instant.parse("2026-08-06T06:05:00Z"));

    List<StopPrediction> predictions = GtfsPredictionUtil.predict(chain, betweenCandB, Instant.now());

    assertThat(predictions).extracting(StopPrediction::stopId).containsExactly(STATION_B, STATION_A);
    assertThat(predictions).extracting(StopPrediction::departure).containsExactly(B_ON_TIME, C_ON_TIME);
  }

  @Test
  void aShortTurnStopsPredictingAtItsOwnTerminus() throws Exception {
    LiveTrip chain = chain();
    GtfsTripInfo shortTurn = trip(0, List.of(
      stopTime(1, A, "08:00:00", 0.0),
      stopTime(2, B, "08:10:00", 111.0)));
    LiveVehicle betweenAandB = vehicle(chain, shortTurn, 59.0005, LON, Instant.parse("2026-08-06T06:05:00Z"));

    List<StopPrediction> predictions = GtfsPredictionUtil.predict(chain, betweenAandB, Instant.now());

    assertThat(predictions).extracting(StopPrediction::stopId).containsExactly(STATION_B);
  }

  @Test
  void aVehicleFarOffItsRouteFallsBackToTheTimetable() throws Exception {
    LiveTrip chain = chain();
    // Nearly three kilometres east of the line, and reporting an hour late. Neither number is trustworthy,
    // so the delay must be discarded rather than applied.
    LiveVehicle astray = vehicle(chain, downwardTrip(), 59.0005, 18.05, Instant.parse("2026-08-06T07:05:00Z"));

    List<StopPrediction> predictions = GtfsPredictionUtil.predict(chain, astray, Instant.now());

    assertThat(predictions).extracting(StopPrediction::departure).containsExactly(B_ON_TIME, C_ON_TIME);
  }

  @Test
  void anImplausiblyLargeDelayIsClamped() throws Exception {
    LiveTrip chain = chain();
    // At B, but reporting eighty minutes after it was due — beyond the point where the number says more
    // about a bad trip match than about the traffic.
    LiveVehicle atB = vehicle(chain, downwardTrip(), 59.001, LON, B_ON_TIME.plusSeconds(80 * 60));

    List<StopPrediction> predictions = GtfsPredictionUtil.predict(chain, atB, Instant.now());

    assertThat(predictions).extracting(StopPrediction::departure)
      .containsExactly(B_ON_TIME.plusSeconds(30 * 60), C_ON_TIME.plusSeconds(30 * 60));
  }

  /** A vehicle standing at its terminus still has a departure time there, and nothing beyond it. */
  @Test
  void aVehicleAtTheEndOfTheChainPredictsOnlyThatStop() throws Exception {
    LiveTrip chain = chain();
    LiveVehicle atC = vehicle(chain, downwardTrip(), 59.002, LON, C_ON_TIME);

    List<StopPrediction> predictions = GtfsPredictionUtil.predict(chain, atC, Instant.now());

    assertThat(predictions).extracting(StopPrediction::stopId).containsExactly(STATION_C);
  }

  @Test
  void aTripWithTooFewStopsPredictsNothing() throws Exception {
    LiveTrip chain = chain();
    GtfsTripInfo oneStop = trip(0, List.of(stopTime(1, A, "08:00:00", 0.0)));
    LiveVehicle atA = vehicle(chain, oneStop, 59.000, LON, A_ON_TIME);

    List<StopPrediction> predictions = GtfsPredictionUtil.predict(chain, atA, Instant.now());

    assertThat(predictions).isEmpty();
  }
}
