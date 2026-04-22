package com.tarnvik.publicbackend.commuter.model.gtfs;

import com.google.transit.realtime.GtfsRealtime.VehiclePosition.VehicleStopStatus;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import static lombok.AccessLevel.PRIVATE;

@Data
@Builder
public class GtfsVehiclePosition implements GeoPosition {
  // Always present — primary join key to static data
  private String tripId;

  // Always present
  private double lat;
  private double lng;

  // Always present — IN_TRANSIT_TO, STOPPED_AT, INCOMING_AT
  @Getter(PRIVATE)
  private VehicleStopStatus currentStatus;                      // Useless, always IN_TRANSIT_TO in practice

  // Always present — Unix timestamp of the position report
  private long timestamp;

  // Sometimes present — 0.0f means absent
  private float bearing;

  // Unit: meters per second (GTFS-RT spec). Appears populated for buses; trains always 0.0f or ~-0.3f (noise)
  private float speed;

  // Not reliably present in RT feed — derive direction from static data instead
  @Getter(PRIVATE)
  private int directionId;

  // Never populated by Samtrafiken — always 0, do not use
  @Getter(PRIVATE)
  private int currentStopSequence;

  // Never populated by Samtrafiken — always empty, do not use
  @Getter(PRIVATE)
  private String stopId;

  // Never populated by Samtrafiken — route_id is always empty in the RT feed
  @Getter(PRIVATE)
  private String routeId;

  // Reliability unknown — included for completeness, may be cleaned up later
  private String vehicleId;    // vehicle.id
  @Getter(PRIVATE)
  private String vehicleLabel; // vehicle.label
}
