package com.tarnvik.publicbackend.commuter.model.gtfs;

import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsNoParentForStopException;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.GtfsParent;
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

  public GtfsParent getParent() throws GtfsNoParentForStopException {
    if (hasParentStation()) {
      return new GtfsParent(parentStation.getStopId(), parentStation.getStopName());
    }
    throw new GtfsNoParentForStopException(this);
  }
}
