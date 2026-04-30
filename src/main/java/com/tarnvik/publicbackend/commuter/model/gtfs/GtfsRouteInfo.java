package com.tarnvik.publicbackend.commuter.model.gtfs;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsMonitoredRoute;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class GtfsRouteInfo {
  @EqualsAndHashCode.Include
  private final String routeId;

  private final String routeShortName;
  private final String routeLongName;
  private final int routeType;
  @ToString.Exclude
  private final GtfsMonitoredRoute monitoredRoute;
}
