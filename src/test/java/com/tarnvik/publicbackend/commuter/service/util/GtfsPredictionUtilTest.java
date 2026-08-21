package com.tarnvik.publicbackend.commuter.service.util;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripUpdate;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripUpdate.StopDelay;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsVehiclePosition;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveTrip;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveVehicle;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.DelaySource;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.StopPrediction;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.VehiclePrediction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
  /** Midway between A and B, and between B and C — on time for either halfway point. */
  private static final Instant HALFWAY = Instant.parse("2026-08-06T06:05:00Z");
  private static final Instant AN_HOUR_LATE = Instant.parse("2026-08-06T07:05:00Z");

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

  /** Most of these tests are about the geometry, which is what runs when SL has measured nothing. */
  private static VehiclePrediction predict(LiveTrip chain, LiveVehicle vehicle, Instant observedAt) {
    return GtfsPredictionUtil.predict(chain, vehicle, observedAt, null);
  }

  @Test
  void aPunctualVehiclePredictsItsTimetable() throws Exception {
    LiveTrip chain = chain();
    LiveVehicle atB = vehicle(chain, downwardTrip(), 59.001, LON, B_ON_TIME);

    VehiclePrediction prediction = predict(chain, atB, B_ON_TIME);

    assertThat(prediction.stopPredictions()).extracting(StopPrediction::stopId)
      .containsExactly(STATION_B, STATION_C);
    assertThat(prediction.stopPredictions()).extracting(StopPrediction::departure)
      .containsExactly(B_ON_TIME, C_ON_TIME);
    assertThat(prediction.delay()).isEqualTo(Duration.ZERO);
    assertThat(prediction.delaySource()).isEqualTo(DelaySource.GEOMETRY);
  }

  @Test
  void aLateVehicleShiftsEveryRemainingStopByItsDelay() throws Exception {
    LiveTrip chain = chain();
    // At B, but reporting five minutes after it was due there.
    LiveVehicle atB = vehicle(chain, downwardTrip(), 59.001, LON, B_ON_TIME.plusSeconds(300));

    VehiclePrediction prediction = predict(chain, atB, B_ON_TIME.plusSeconds(300));

    assertThat(prediction.stopPredictions()).extracting(StopPrediction::departure)
      .containsExactly(B_ON_TIME.plusSeconds(300), C_ON_TIME.plusSeconds(300));
    assertThat(prediction.delay()).isEqualTo(Duration.ofMinutes(5));
    assertThat(prediction.delaySource()).isEqualTo(DelaySource.GEOMETRY);
  }

  @Test
  void anUpwardVehiclePredictsBackAlongTheChain() throws Exception {
    LiveTrip chain = chain();
    // Halfway between B and C, running the other way, on time for that point.
    LiveVehicle betweenCandB = vehicle(chain, upwardTrip(), 59.0015, LON, HALFWAY);

    VehiclePrediction prediction = predict(chain, betweenCandB, HALFWAY);

    assertThat(prediction.stopPredictions()).extracting(StopPrediction::stopId)
      .containsExactly(STATION_B, STATION_A);
    assertThat(prediction.stopPredictions()).extracting(StopPrediction::departure)
      .containsExactly(B_ON_TIME, C_ON_TIME);
  }

  @Test
  void aShortTurnStopsPredictingAtItsOwnTerminus() throws Exception {
    LiveTrip chain = chain();
    GtfsTripInfo shortTurn = trip(0, List.of(
      stopTime(1, A, "08:00:00", 0.0),
      stopTime(2, B, "08:10:00", 111.0)));
    LiveVehicle betweenAandB = vehicle(chain, shortTurn, 59.0005, LON, HALFWAY);

    VehiclePrediction prediction = predict(chain, betweenAandB, HALFWAY);

    assertThat(prediction.stopPredictions()).extracting(StopPrediction::stopId).containsExactly(STATION_B);
  }

  @Test
  void aVehicleFarOffItsRouteFallsBackToTheTimetable() throws Exception {
    LiveTrip chain = chain();
    // Nearly three kilometres east of the line, and reporting an hour late. Neither number is trustworthy,
    // so the delay must be discarded rather than applied.
    LiveVehicle astray = vehicle(chain, downwardTrip(), 59.0005, 18.05, AN_HOUR_LATE);

    VehiclePrediction prediction = predict(chain, astray, AN_HOUR_LATE);

    assertThat(prediction.stopPredictions()).extracting(StopPrediction::departure)
      .containsExactly(B_ON_TIME, C_ON_TIME);
    // Null rather than zero: a zero here would claim the vehicle was measured and found punctual.
    assertThat(prediction.delay()).isNull();
    assertThat(prediction.delaySource()).isNull();
  }

  @Test
  void anImplausiblyLargeDelayIsClamped() throws Exception {
    LiveTrip chain = chain();
    // At B, but reporting eighty minutes after it was due — beyond the point where the number says more
    // about a bad trip match than about the traffic.
    LiveVehicle atB = vehicle(chain, downwardTrip(), 59.001, LON, B_ON_TIME.plusSeconds(80 * 60));

    VehiclePrediction prediction = predict(chain, atB, B_ON_TIME.plusSeconds(80 * 60));

    assertThat(prediction.stopPredictions()).extracting(StopPrediction::departure)
      .containsExactly(B_ON_TIME.plusSeconds(30 * 60), C_ON_TIME.plusSeconds(30 * 60));
    assertThat(prediction.delay()).isEqualTo(Duration.ofMinutes(30));
  }

  /** A vehicle standing at its terminus still has a departure time there, and nothing beyond it. */
  @Test
  void aVehicleAtTheEndOfTheChainPredictsOnlyThatStop() throws Exception {
    LiveTrip chain = chain();
    LiveVehicle atC = vehicle(chain, downwardTrip(), 59.002, LON, C_ON_TIME);

    VehiclePrediction prediction = predict(chain, atC, C_ON_TIME);

    assertThat(prediction.stopPredictions()).extracting(StopPrediction::stopId).containsExactly(STATION_C);
  }

  /**
   * The vehicle is past B and running to time by its position, but SL recorded it three minutes late
   * leaving B. The measurement wins: it is a platform reading rather than a projection onto a straight
   * line, and every remaining stop is shifted by it.
   */
  @Test
  void aMeasuredDelayIsPreferredToTheGeometricOne() throws Exception {
    LiveTrip chain = chain();
    LiveVehicle betweenBandC = vehicle(chain, downwardTrip(), 59.0015, LON, HALFWAY);
    GtfsTripUpdate measured = new GtfsTripUpdate("t1", List.of(
      new StopDelay(1, 12), new StopDelay(2, 180), new StopDelay(3, 0)));

    VehiclePrediction prediction = GtfsPredictionUtil.predict(chain, betweenBandC, HALFWAY, measured);

    assertThat(prediction.delay()).isEqualTo(Duration.ofMinutes(3));
    assertThat(prediction.delaySource()).isEqualTo(DelaySource.TRIP_UPDATES);
    assertThat(prediction.stopPredictions()).extracting(StopPrediction::departure)
      .containsExactly(C_ON_TIME.plusSeconds(180));
  }

  /**
   * A vehicle standing at a platform projects onto the segment it has just finished, so it reads the
   * measurement from the stop <em>before</em> the one it is at — here A's twelve seconds rather than B's
   * three minutes, although SL has already recorded the arrival at B.
   * <p>
   * Deliberate, and the safe half of an unavoidable trade. Advancing to the next stop whenever a vehicle
   * projects near it would read a stop the vehicle is merely <em>approaching</em>, and that row carries the
   * timetable's zero — reporting a late vehicle as punctual, which is the one failure this design exists to
   * prevent. The cost is a delay one inter-stop interval stale, over which the figure moves by seconds.
   */
  @Test
  void aVehicleStandingAtAPlatformReadsTheStopBeforeIt() throws Exception {
    LiveTrip chain = chain();
    LiveVehicle atB = vehicle(chain, downwardTrip(), 59.001, LON, B_ON_TIME);
    GtfsTripUpdate measured = new GtfsTripUpdate("t1", List.of(
      new StopDelay(1, 12), new StopDelay(2, 180), new StopDelay(3, 0)));

    VehiclePrediction prediction = GtfsPredictionUtil.predict(chain, atB, B_ON_TIME, measured);

    assertThat(prediction.delay()).isEqualTo(Duration.ofSeconds(12));
    assertThat(prediction.delaySource()).isEqualTo(DelaySource.TRIP_UPDATES);
  }

  /**
   * The vehicle is between A and B, so only the row for A is behind it. Reading the zero on B — which is
   * the timetable restated, not a measurement — would report this late vehicle as punctual.
   */
  @Test
  void aZeroFromAStopAheadIsNotRead() throws Exception {
    LiveTrip chain = chain();
    LiveVehicle betweenAandB = vehicle(chain, downwardTrip(), 59.0005, LON, HALFWAY);
    GtfsTripUpdate measured = new GtfsTripUpdate("t1", List.of(
      new StopDelay(1, 240), new StopDelay(2, 0), new StopDelay(3, 0)));

    VehiclePrediction prediction = GtfsPredictionUtil.predict(chain, betweenAandB, HALFWAY, measured);

    assertThat(prediction.delay()).isEqualTo(Duration.ofMinutes(4));
    assertThat(prediction.delaySource()).isEqualTo(DelaySource.TRIP_UPDATES);
  }

  /** Roughly one vehicle in ten has no passed platform to report, and the geometry still has to answer. */
  @Test
  void aTripTheFeedHasNothingOnFallsBackToTheGeometry() throws Exception {
    LiveTrip chain = chain();
    LiveVehicle atB = vehicle(chain, downwardTrip(), 59.001, LON, B_ON_TIME.plusSeconds(300));
    GtfsTripUpdate nothingPassed = new GtfsTripUpdate("t1", List.of(new StopDelay(3, 0)));

    VehiclePrediction prediction =
      GtfsPredictionUtil.predict(chain, atB, B_ON_TIME.plusSeconds(300), nothingPassed);

    assertThat(prediction.delay()).isEqualTo(Duration.ofMinutes(5));
    assertThat(prediction.delaySource()).isEqualTo(DelaySource.GEOMETRY);
  }

  /**
   * The distance guard belongs to the geometry alone. Asking which stops a vehicle has passed survives a
   * projection that is far too poor to interpolate a scheduled time along, so a measurement is still served
   * where the geometry has to give up.
   */
  @Test
  void aMeasuredDelayIsServedEvenWhereTheGeometryGivesUp() throws Exception {
    LiveTrip chain = chain();
    LiveVehicle astray = vehicle(chain, downwardTrip(), 59.0005, 18.05, AN_HOUR_LATE);
    GtfsTripUpdate measured = new GtfsTripUpdate("t1", List.of(new StopDelay(1, 90)));

    VehiclePrediction prediction = GtfsPredictionUtil.predict(chain, astray, AN_HOUR_LATE, measured);

    assertThat(prediction.delay()).isEqualTo(Duration.ofSeconds(90));
    assertThat(prediction.delaySource()).isEqualTo(DelaySource.TRIP_UPDATES);
  }

  @Test
  void aTripWithTooFewStopsPredictsNothing() throws Exception {
    LiveTrip chain = chain();
    GtfsTripInfo oneStop = trip(0, List.of(stopTime(1, A, "08:00:00", 0.0)));
    LiveVehicle atA = vehicle(chain, oneStop, 59.000, LON, A_ON_TIME);

    VehiclePrediction prediction = predict(chain, atA, A_ON_TIME);

    assertThat(prediction.stopPredictions()).isEmpty();
    assertThat(prediction.delay()).isNull();
    assertThat(prediction.delaySource()).isNull();
  }
}
