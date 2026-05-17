package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.variations;

import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveForkStop;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class RouteForkVariant extends RouteVariant {
  private final List<LiveForkStop> liveStops;
}
