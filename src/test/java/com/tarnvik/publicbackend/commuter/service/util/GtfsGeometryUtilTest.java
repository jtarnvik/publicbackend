package com.tarnvik.publicbackend.commuter.service.util;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsStop;
import com.tarnvik.publicbackend.commuter.service.util.GtfsGeometryUtil.VehicleLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Three stops on a straight north-south line at longitude 18.0, latitude 59.0.
 * Each 0.001° of latitude ≈ 111 m.
 *
 *   C  (59.002, 18.000)
 *   |   ← segment 1
 *   B  (59.001, 18.000)
 *   |   ← segment 0
 *   A  (59.000, 18.000)
 */
class GtfsGeometryUtilTest {
  private static final GtfsStop A = stop(59.000, 18.000);
  private static final GtfsStop B = stop(59.001, 18.000);
  private static final GtfsStop C = stop(59.002, 18.000);
  private static final List<GtfsStop> STOPS = List.of(A, B, C);

  private static final double DIST_TOLERANCE_METRES = 1.0;
  private static final double T_TOLERANCE = 0.01;

  private static GtfsStop stop(double lat, double lng) {
    GtfsStop s = new GtfsStop();
    s.setStopLat(lat);
    s.setStopLon(lng);
    return s;
  }

  @Test
  void vehicleAtFirstStop_returnsSegment0_t0_distZero() {
    VehicleLocation result = GtfsGeometryUtil.locateOnRoute(STOPS, A);

    assertThat(result.segIdx()).isEqualTo(0);
    assertThat(result.t()).isCloseTo(0.0, within(T_TOLERANCE));
    assertThat(result.dist()).isCloseTo(0.0, within(DIST_TOLERANCE_METRES));
  }

  @Test
  void vehicleHalfwayBetweenFirstAndSecondStop_returnsSegment0_tHalf_distZero() {
    GtfsStop midAB = stop(59.0005, 18.000);

    VehicleLocation result = GtfsGeometryUtil.locateOnRoute(STOPS, midAB);

    assertThat(result.segIdx()).isEqualTo(0);
    assertThat(result.t()).isCloseTo(0.5, within(T_TOLERANCE));
    assertThat(result.dist()).isCloseTo(0.0, within(DIST_TOLERANCE_METRES));
  }

  @Test
  void vehicleAtLastStop_returnsLastSegment_t1_distZero() {
    VehicleLocation result = GtfsGeometryUtil.locateOnRoute(STOPS, C);

    assertThat(result.segIdx()).isEqualTo(1);
    assertThat(result.t()).isCloseTo(1.0, within(T_TOLERANCE));
    assertThat(result.dist()).isCloseTo(0.0, within(DIST_TOLERANCE_METRES));
  }

  @Test
  void vehicleBeyondLastStop_returnsLastSegment_t1_distNonZero() {
    GtfsStop beyondC = stop(59.003, 18.000); // ~111 m past C

    VehicleLocation result = GtfsGeometryUtil.locateOnRoute(STOPS, beyondC);

    assertThat(result.segIdx()).isEqualTo(1);
    assertThat(result.t()).isCloseTo(1.0, within(T_TOLERANCE));
    assertThat(result.dist()).isGreaterThan(100.0);
    assertThat(result.dist()).isLessThan(120.0);
  }

  @Test
  void vehicleBeforeFirstStop_returnsSegment0_t0_distNonZero() {
    GtfsStop beforeA = stop(58.999, 18.000); // ~111 m before A

    VehicleLocation result = GtfsGeometryUtil.locateOnRoute(STOPS, beforeA);

    assertThat(result.segIdx()).isEqualTo(0);
    assertThat(result.t()).isCloseTo(0.0, within(T_TOLERANCE));
    assertThat(result.dist()).isGreaterThan(100.0);
    assertThat(result.dist()).isLessThan(120.0);
  }

  @Test
  void vehiclePerpendicularToMidSegment_returnsCorrectSegmentAndOffset() {
    // Midpoint of AB offset ~57 m east (0.001° lon * cos(59°) ≈ 57 m)
    GtfsStop offsetMidAB = stop(59.0005, 18.001);

    VehicleLocation result = GtfsGeometryUtil.locateOnRoute(STOPS, offsetMidAB);

    assertThat(result.segIdx()).isEqualTo(0);
    assertThat(result.t()).isCloseTo(0.5, within(T_TOLERANCE));
    assertThat(result.dist()).isGreaterThan(50.0);
    assertThat(result.dist()).isLessThan(65.0);
  }
}
