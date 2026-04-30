package com.tarnvik.publicbackend.commuter.model.gtfs.exception;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;

public class GtfsNoParentForStopException extends GtfsLiveException {
  public GtfsNoParentForStopException(GtfsStopInfo stop) {
    super("No parent for stop: " + stop.getStopId() + "/" + stop.getStopName());
  }
}
