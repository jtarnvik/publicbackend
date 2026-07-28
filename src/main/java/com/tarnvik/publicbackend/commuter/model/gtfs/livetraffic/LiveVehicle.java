package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsVehiclePosition;
import com.tarnvik.publicbackend.commuter.service.util.GtfsGeometryUtil.VehicleLocation;
import lombok.Builder;
import lombok.Value;

/**
 * One vehicle currently running on a monitored route: where it reported itself to be, where that places it
 * on the route group's stop chain, and which scheduled journey it is running.
 * <p>
 * {@code location} indexes into the chain held by the enclosing {@link RouteData}, <em>not</em> into this
 * vehicle's own trip. That is the point of placing every vehicle on one shared chain: two vehicles with the
 * same {@code segIdx} really are between the same two stations, whichever short turn or variant each runs.
 * <p>
 * Which way the vehicle moves along that chain is {@code trip.directionId} compared against the chain's own
 * {@code direction} — both are plain direction ids, so the comparison belongs wherever the two are in hand.
 */
@Value
@Builder
public class LiveVehicle {
  /** The raw realtime report from the GTFS-RT feed. */
  GtfsVehiclePosition position;

  /** Where {@code position} falls on the route group's stop chain — which segment, and how far along it. */
  VehicleLocation location;

  /** The static trip the vehicle is running, including its own ordered stop times. */
  GtfsTripInfo trip;
}
