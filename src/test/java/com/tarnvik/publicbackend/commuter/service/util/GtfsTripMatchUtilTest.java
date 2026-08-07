package com.tarnvik.publicbackend.commuter.service.util;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsMonitoredRoute;
import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsRouteInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.TripQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Modelled on the real collision this matching exists to survive. At Älvsjö (stop area 5141) two line 43
 * departures leave at exactly 10:00:00 on 2026-08-07, one for Västerhaninge and one for Kungsängen, and the
 * destination is the only thing that separates them.
 *
 * <pre>
 *   Kungsängen  ← direction 0 ends here
 *       |
 *    Älvsjö     10:00:00 either way
 *       |
 *   Västerhaninge  ← direction 1 ends here
 * </pre>
 *
 * All times are Stockholm wall clock, so 10:00 local is 08:00Z on a summer date.
 */
class GtfsTripMatchUtilTest {
  private static final int ALVSJO_AREA = 5141;
  private static final String ALVSJO_STATION = "9021001005141000";
  private static final Instant TEN_OCLOCK = Instant.parse("2026-08-07T08:00:00Z");

  private static final GtfsStopInfo KUNGSANGEN = platform("9021001006081000", "Kungsängen", 59.478);
  private static final GtfsStopInfo ALVSJO = platform(ALVSJO_STATION, "Älvsjö", 59.278);
  private static final GtfsStopInfo VASTERHANINGE = platform("9021001007541000", "Västerhaninge", 59.121);

  private static GtfsStopInfo platform(String parentId, String name, double lat) {
    GtfsStopInfo parent = GtfsStopInfo.builder()
      .stopId(parentId).stopName(name).stopLat(lat).stopLon(18.0).locationType(1).build();
    return GtfsStopInfo.builder()
      .stopId("9022" + parentId.substring(4)).stopName(name).stopLat(lat).stopLon(18.0)
      .locationType(0).parentStation(parent).build();
  }

  private static GtfsStopTimeInfo stopTime(int sequence, GtfsStopInfo stop, String time, String headsign) {
    return GtfsStopTimeInfo.builder()
      .stopSequence(sequence).stop(stop).arrivalTime(time).departureTime(time).stopHeadsign(headsign).build();
  }

  private static GtfsRouteInfo route(String shortName) {
    GtfsMonitoredRoute monitored = new GtfsMonitoredRoute();
    monitored.setRouteShortName(shortName);
    monitored.setTransportMode(TransportMode.TRAIN);
    monitored.setRouteGroup(1);
    return new GtfsRouteInfo("9011001004300000", shortName, "", 100, monitored);
  }

  private static GtfsTripInfo trip(String tripId, String shortName, int directionId,
                                   List<GtfsStopTimeInfo> stopTimes) {
    return GtfsTripInfo.builder()
      .tripId(tripId).directionId(directionId).routeInfo(route(shortName)).stopTimes(stopTimes).build();
  }

  /** Southbound: Kungsängen → Älvsjö → Västerhaninge, calling at Älvsjö at 10:00:00. */
  private static GtfsTripInfo towardsVasterhaninge() {
    return trip("towards-vasterhaninge", "43", 1, List.of(
      stopTime(1, KUNGSANGEN, "09:20:00", "Västerhaninge"),
      stopTime(2, ALVSJO, "10:00:00", "Västerhaninge"),
      stopTime(3, VASTERHANINGE, "10:30:00", "Västerhaninge")));
  }

  /** Northbound: the same three stations the other way about, also at Älvsjö at 10:00:00. */
  private static GtfsTripInfo towardsKungsangen() {
    return trip("towards-kungsangen", "43", 0, List.of(
      stopTime(1, VASTERHANINGE, "09:30:00", "Kungsängen"),
      stopTime(2, ALVSJO, "10:00:00", "Kungsängen"),
      stopTime(3, KUNGSANGEN, "10:40:00", "Kungsängen")));
  }

  private static TripQuery.TripQueryBuilder queryAtAlvsjo() {
    return TripQuery.builder()
      .transportMode(TransportMode.TRAIN)
      .routeGroup(1)
      .line("43")
      .stopAreaId(ALVSJO_AREA)
      .stopAreaName("Älvsjö")
      .scheduled(TEN_OCLOCK);
  }

  private static boolean matches(GtfsTripInfo trip, TripQuery query) {
    return GtfsTripMatchUtil.matches(trip, query, ALVSJO_STATION, TEN_OCLOCK);
  }

  @Test
  void stopAreaIdBecomesTheParentStationId() {
    assertThat(GtfsTripMatchUtil.toParentStationId(ALVSJO_AREA)).isEqualTo(ALVSJO_STATION);
    assertThat(GtfsTripMatchUtil.toParentStationId(12273)).isEqualTo("9021001012273000");
    assertThat(GtfsTripMatchUtil.toParentStationId(6081)).isEqualTo("9021001006081000");
  }

  @Test
  void theDestinationSeparatesTwoDeparturesAtTheSameSecond() {
    TripQuery southbound = queryAtAlvsjo().destination("Västerhaninge").build();

    assertThat(matches(towardsVasterhaninge(), southbound)).isTrue();
    assertThat(matches(towardsKungsangen(), southbound)).isFalse();
  }

  @Test
  void theOppositeDirectionMatchesItsOwnDeparture() {
    TripQuery northbound = queryAtAlvsjo().destination("Kungsängen").build();

    assertThat(matches(towardsKungsangen(), northbound)).isTrue();
    assertThat(matches(towardsVasterhaninge(), northbound)).isFalse();
  }

  @Test
  void aVariantSuffixIsStillTheSameLine() {
    TripQuery query = queryAtAlvsjo().line("43X").destination("Västerhaninge").build();

    assertThat(matches(towardsVasterhaninge(), query)).isTrue();
  }

  @Test
  void anotherLineInTheSameGroupDoesNotMatch() {
    TripQuery query = queryAtAlvsjo().line("44").destination("Västerhaninge").build();

    assertThat(matches(towardsVasterhaninge(), query)).isFalse();
  }

  @Test
  void aDifferentScheduledTimeDoesNotMatch() {
    TripQuery query = queryAtAlvsjo()
      .scheduled(TEN_OCLOCK.plusSeconds(900))
      .destination("Västerhaninge")
      .build();

    assertThat(matches(towardsVasterhaninge(), query)).isFalse();
  }

  @Test
  void aFewSecondsOfDriftStillMatches() {
    TripQuery query = queryAtAlvsjo()
      .scheduled(TEN_OCLOCK.plusSeconds(20))
      .destination("Västerhaninge")
      .build();

    assertThat(matches(towardsVasterhaninge(), query)).isTrue();
  }

  @Test
  void aStationTheTripNeverCallsAtDoesNotMatch() {
    TripQuery query = queryAtAlvsjo().destination("Västerhaninge").build();

    // A short turn that terminates before Älvsjö, so nothing on it can be the 10:00 from there.
    GtfsTripInfo shortTurn = trip("short-turn", "43", 1, List.of(
      stopTime(1, KUNGSANGEN, "09:20:00", "Västerhaninge")));

    assertThat(GtfsTripMatchUtil.matches(shortTurn, query, ALVSJO_STATION, TEN_OCLOCK)).isFalse();
  }

  @Test
  void destinationComparisonIgnoresCasingAndStrayWhitespace() {
    TripQuery query = queryAtAlvsjo().destination("  västerhaninge ").build();

    assertThat(matches(towardsVasterhaninge(), query)).isTrue();
  }
}
