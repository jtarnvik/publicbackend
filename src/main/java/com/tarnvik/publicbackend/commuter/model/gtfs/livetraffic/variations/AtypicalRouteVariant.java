package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.variations;

import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.GtfsParent;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AtypicalRouteVariant extends RouteVariant {
  private final GtfsParent atypical;
  private final String infoMessage;
}
