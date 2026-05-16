package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.variations;

import com.tarnvik.publicbackend.commuter.model.gtfs.ParentStopIdentifier;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AtypicalRouteVariant extends RouteVariant {
  private final ParentStopIdentifier stopIdentifier;
  private final String infoMessage;
}
