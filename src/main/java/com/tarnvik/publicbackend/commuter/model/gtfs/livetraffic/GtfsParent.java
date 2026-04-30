package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

import lombok.Data;

@Data
public class GtfsParent {
  private final String stopId;
  private final String stopName;
}
