package com.tarnvik.publicbackend.commuter.model.gtfs;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class GtfsTripInfo {
  String tripId;
  int directionId;
  String serviceId;
  GtfsRouteInfo routeInfo;
  List<GtfsStopTimeInfo> stopTimes;
}
