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
