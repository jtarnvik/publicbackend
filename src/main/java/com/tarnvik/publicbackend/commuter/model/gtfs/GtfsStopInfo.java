package com.tarnvik.publicbackend.commuter.model.gtfs;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GtfsStopInfo implements GeoPosition {
  String stopId;
  String stopName;
  double stopLat;
  double stopLon;
  Integer locationType;
  GtfsStopInfo parentStation;

  @Override
  public double getLat() {
    return stopLat;
  }

  @Override
  public double getLng() {
    return stopLon;
  }

  public boolean hasParentStation() {
    return parentStation != null;
  }
}
