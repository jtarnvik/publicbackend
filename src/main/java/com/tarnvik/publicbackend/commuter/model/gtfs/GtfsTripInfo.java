package com.tarnvik.publicbackend.commuter.model.gtfs;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class GtfsTripInfo {
  private final String tripId;
  private final int directionId;
  private final String serviceId;
  private final GtfsRoute route;
}
