package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Three stops A → B → C (south to north), direction 0.
 * Distances: A = 0 m, B = 1000 m, C = 2500 m (total = 2500 m).
 *
 * After reversal the order is C → B → A, direction 1, heading "Stop A".
 */
class LiveTripTest {
  private LiveTrip trip;

  @BeforeEach
  void setUp() throws Exception {
    GtfsStopInfo parentA = GtfsStopInfo.builder().stopId("PA").stopName("Stop A").stopLat(59.0).stopLon(18.0).build();
    GtfsStopInfo stopA = GtfsStopInfo.builder().stopId("SA").stopName("A").stopLat(59.0).stopLon(18.0).parentStation(parentA).build();
    GtfsStopTimeInfo stiA = GtfsStopTimeInfo.builder().stop(stopA).shapeDistTraveled(0.0).stopHeadsign("Stop C").stopSequence(1).build();

    GtfsStopInfo parentB = GtfsStopInfo.builder().stopId("PB").stopName("Stop B").stopLat(59.1).stopLon(18.0).build();
    GtfsStopInfo stopB = GtfsStopInfo.builder().stopId("SB").stopName("B").stopLat(59.1).stopLon(18.0).parentStation(parentB).build();
    GtfsStopTimeInfo stiB = GtfsStopTimeInfo.builder().stop(stopB).shapeDistTraveled(1000.0).stopHeadsign("Stop C").stopSequence(2).build();

    GtfsStopInfo parentC = GtfsStopInfo.builder().stopId("PC").stopName("Stop C").stopLat(59.2).stopLon(18.0).build();
    GtfsStopInfo stopC = GtfsStopInfo.builder().stopId("SC").stopName("C").stopLat(59.2).stopLon(18.0).parentStation(parentC).build();
    GtfsStopTimeInfo stiC = GtfsStopTimeInfo.builder().stop(stopC).shapeDistTraveled(2500.0).stopHeadsign("Stop C").stopSequence(3).build();

    GtfsTripInfo tripInfo = GtfsTripInfo.builder()
        .tripId("TRIP1")
        .directionId(0)
        .stopTimes(List.of(stiA, stiB, stiC))
        .build();

    trip = new LiveTrip(tripInfo, List.of());
  }

  @Test
  void reverseTrip_reversesStopOrder() throws Exception {
    trip.reverseTrip();

    List<LiveStop> stops = trip.getLiveStops();
    assertThat(stops).hasSize(3);
    assertThat(stops.get(0).getStopId()).isEqualTo("PC");
    assertThat(stops.get(1).getStopId()).isEqualTo("PB");
    assertThat(stops.get(2).getStopId()).isEqualTo("PA");
  }

  @Test
  void reverseTrip_recalculatesShapeDistTraveled() throws Exception {
    trip.reverseTrip();

    List<LiveStop> stops = trip.getLiveStops();
    assertThat(stops.get(0).getShapeDistTraveled()).isEqualTo(0.0);
    assertThat(stops.get(1).getShapeDistTraveled()).isEqualTo(1500.0);
    assertThat(stops.get(2).getShapeDistTraveled()).isEqualTo(2500.0);
  }

  @Test
  void reverseTrip_recalculatesShapeDistTraveledSinceLast() throws Exception {
    trip.reverseTrip();

    List<LiveStop> stops = trip.getLiveStops();
    assertThat(stops.get(0).getShapeDistTraveledSinceLast()).isEqualTo(0.0);
    assertThat(stops.get(1).getShapeDistTraveledSinceLast()).isEqualTo(1500.0);
    assertThat(stops.get(2).getShapeDistTraveledSinceLast()).isEqualTo(1000.0);
  }

  @Test
  void reverseTrip_sinceLastSumEqualsTotalDistance() throws Exception {
    trip.reverseTrip();

    double sum = trip.getLiveStops().stream()
        .mapToDouble(LiveStop::getShapeDistTraveledSinceLast)
        .sum();
    assertThat(sum).isEqualTo(2500.0);
  }

  @Test
  void reverseTrip_flipsDirectionAndUpdatesHeading() throws Exception {
    trip.reverseTrip();

    assertThat(trip.getDirection()).isEqualTo(1);
    assertThat(trip.getStopHeading()).isEqualTo("Stop A");
  }
}
