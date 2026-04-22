package com.tarnvik.publicbackend.commuter.model.gtfs;

import com.google.transit.realtime.GtfsRealtime.VehiclePosition.VehicleStopStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GtfsVehiclePosition {
  // Always present — primary join key to static data
  private String tripId;

  // Always present
  private float latitude;
  private float longitude;

  // Always present — IN_TRANSIT_TO, STOPPED_AT, INCOMING_AT
  private VehicleStopStatus currentStatus;

  // Always present — Unix timestamp of the position report
  private long timestamp;

  // Sometimes present — 0.0f means absent
  private float bearing;

  // Sometimes present — 0.0f means absent
  private float speed;

  // Not reliably present in RT feed — derive direction from static data instead
  private int directionId;

  // Never populated by Samtrafiken — always 0, do not use
  private int currentStopSequence;

  // Never populated by Samtrafiken — always empty, do not use
  private String stopId;

  // Never populated by Samtrafiken — route_id is always empty in the RT feed
  private String routeId;

  // Reliability unknown — included for completeness, may be cleaned up later
  private String vehicleId;    // vehicle.id
  private String vehicleLabel; // vehicle.label
}
