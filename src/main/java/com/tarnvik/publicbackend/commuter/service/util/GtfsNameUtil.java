package com.tarnvik.publicbackend.commuter.service.util;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsMonitoredRoute;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class GtfsNameUtil {
  private GtfsNameUtil() {}

  /**
   * Returns true if {@code routeShortName} matches the given monitored route's base name,
   * allowing for an optional trailing letter suffix (e.g. "43X" matches monitored route "43").
   */
  public static boolean matchesMonitoredRouteName(String routeShortName, GtfsMonitoredRoute monitoredRoute) {
    String base = Pattern.quote(monitoredRoute.getRouteShortName());
    return routeShortName.matches("^" + base + "[A-Za-z]?$");
  }

  /**
   * Whether two line designations name the same line, ignoring a variant suffix on either side — "43X" and
   * "43" are the same line, and so are "43X" and "43Y".
   * <p>
   * Needed because both sides may carry a suffix: SL's {@code line.designation} and the GTFS
   * {@code route_short_name} are independent spellings of the same line, whereas
   * {@link #matchesMonitoredRouteName} compares against a configured base name that never has one.
   * <p>
   * The frontend's {@code baseLineName} in {@code util/route-group.ts} applies the same rule. The two have
   * to agree: a designation stripped differently there would offer the user a group whose data does not in
   * fact contain that vehicle.
   */
  public static boolean sameLine(String designation, String otherDesignation) {
    if (designation == null || otherDesignation == null) {
      return false;
    }
    return baseLineName(designation).equalsIgnoreCase(baseLineName(otherDesignation));
  }

  /** A designation without its variant suffix — one optional trailing letter, the same rule used above. */
  private static String baseLineName(String designation) {
    return designation.trim().replaceAll("[A-Za-z]$", "");
  }

  /**
   * Finds the first {@link GtfsMonitoredRoute} whose base name matches {@code routeShortName},
   * ignoring transport mode. Suitable for dataset assembly where all routes are already filtered
   * to monitored ones.
   */
  public static Optional<GtfsMonitoredRoute> findMatchingMonitoredRoute(String routeShortName,
                                                                        List<GtfsMonitoredRoute> monitoredRoutes) {
    return monitoredRoutes.stream()
      .filter(m -> matchesMonitoredRouteName(routeShortName, m))
      .findFirst();
  }
}
