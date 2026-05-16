package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

import com.tarnvik.publicbackend.commuter.model.gtfs.ParentStopIdentifier;
import lombok.Data;

@Data
public class GtfsParent {
  private final String stopId;
  private final String stopName;

  public boolean matches(ParentStopIdentifier identifier) {
    return this.stopId.equals(identifier.getId());
  }
}
