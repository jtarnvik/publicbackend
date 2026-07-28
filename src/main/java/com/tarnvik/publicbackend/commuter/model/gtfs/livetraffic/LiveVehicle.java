package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsVehiclePosition;
import com.tarnvik.publicbackend.commuter.service.util.GtfsGeometryUtil.VehicleLocation;
import lombok.Builder;
import lombok.Value;

import java.util.List;

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

  /**
   * Where this vehicle actually terminates, which is not necessarily the end of the group's chain — short
   * turns are routine on the metro, and on 117 at rush hour. Taken from the vehicle's own trip, so a short
   * turn reports the stop it really ends at.
   * <p>
   * {@code stop_headsign} is the operator's own destination text and is consistent across every stop time in
   * a trip, which makes it the right thing to show a passenger. It is nullable in the feed, so the last
   * stop's parent station is the fallback.
   */
  public String getDestination() {
    List<GtfsStopTimeInfo> stopTimes = trip.getStopTimes();
    if (stopTimes.isEmpty()) {
      return null;
    }
    String headsign = stopTimes.getFirst().getStopHeadsign();
    if (headsign != null && !headsign.isBlank()) {
      return headsign;
    }
    GtfsStopInfo lastStop = stopTimes.getLast().getStop();
    return lastStop.hasParentStation() ? lastStop.getParentStation().getStopName() : lastStop.getStopName();
  }
}
