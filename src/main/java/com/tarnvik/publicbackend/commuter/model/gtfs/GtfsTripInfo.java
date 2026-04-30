package com.tarnvik.publicbackend.commuter.model.gtfs;

import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.GroupKey;
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

  public GroupKey getGroupKey() {
    return routeInfo.getMonitoredRoute().getGroupKey();
  }
}
