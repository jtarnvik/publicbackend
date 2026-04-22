package com.tarnvik.publicbackend.commuter.service.util;

import com.tarnvik.publicbackend.commuter.model.gtfs.GeoPosition;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class GtfsGeometryUtil {
  private static final double EARTH_RADIUS_METRES = 6_371_000.0;

  /**
   * The result of locating a vehicle on a route.
   *
   * @param segIdx index of the first stop in the closest segment (0 = between stop 0 and stop 1)
   * @param t      position along that segment as a fraction in [0, 1], where 0 is the first stop
   *               and 1 is the second stop
   * @param dist   perpendicular distance in metres from the vehicle to the closest point on the segment
   */
  public record VehicleLocation(int segIdx, double t, double dist) {}

  private record SegmentProjection(int segIdx, double t, double dist, GeoPosition a, GeoPosition b) {}

  public static VehicleLocation locateOnRoute(List<? extends GeoPosition> stops, GeoPosition vehiclePos) {
    SegmentProjection best = null;

    for (int i = 0; i < stops.size() - 1; i++) {
      SegmentProjection proj = projectOntoSegment(i, stops.get(i), stops.get(i + 1), vehiclePos);
      log.trace("Segment {}: t={}, dist={}", proj.segIdx(), proj.t(), proj.dist());
      if (best == null || proj.dist() < best.dist()) {
        best = proj;
      }
    }

    VehicleLocation result = new VehicleLocation(best.segIdx(), best.t(), best.dist());
    log.debug("Best segment: segIdx={}, t={}, dist={}", result.segIdx(), result.t(), result.dist());
    return result;
  }

  private static SegmentProjection projectOntoSegment(int segIdx, GeoPosition a, GeoPosition b, GeoPosition vehiclePos) {
    double cosLat = Math.cos(Math.toRadians(a.getLat()));

    double abX = (b.getLng() - a.getLng()) * cosLat;
    double abY = b.getLat() - a.getLat();
    double apX = (vehiclePos.getLng() - a.getLng()) * cosLat;
    double apY = vehiclePos.getLat() - a.getLat();

    double abDotAb = abX * abX + abY * abY;
    double t;
    if (abDotAb == 0.0) {
      t = 0.0;
    } else {
      t = (apX * abX + apY * abY) / abDotAb;
      t = Math.max(0.0, Math.min(1.0, t));
    }

    double closestLat = a.getLat() + t * (b.getLat() - a.getLat());
    double closestLng = a.getLng() + t * (b.getLng() - a.getLng());
    double dist = haversineMetres(vehiclePos.getLat(), vehiclePos.getLng(), closestLat, closestLng);

    return new SegmentProjection(segIdx, t, dist, a, b);
  }

  private static double haversineMetres(double lat1, double lng1, double lat2, double lng2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double sinDLat = Math.sin(dLat / 2);
    double sinDLng = Math.sin(dLng / 2);
    double h = sinDLat * sinDLat
      + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinDLng * sinDLng;
    return 2 * EARTH_RADIUS_METRES * Math.asin(Math.sqrt(h));
  }
}
