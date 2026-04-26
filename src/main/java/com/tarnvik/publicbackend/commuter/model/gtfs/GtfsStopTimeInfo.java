package com.tarnvik.publicbackend.commuter.model.gtfs;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GtfsStopTimeInfo {
  int stopSequence;
  GtfsStopInfo stop;
  String arrivalTime;
  String departureTime;
  Double shapeDistTraveled;
  String stopHeadsign;
}
