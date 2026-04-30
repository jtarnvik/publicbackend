package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.variations;

import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.GtfsParent;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EndStopRouteVariant extends RouteVariant {
  private final GtfsParent expectedEndStop;
}
