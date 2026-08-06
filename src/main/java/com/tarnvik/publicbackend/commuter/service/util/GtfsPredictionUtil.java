package com.tarnvik.publicbackend.commuter.service.util;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveStop;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveTrip;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveVehicle;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.StopPrediction;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.util.GtfsUtil;
import com.tarnvik.publicbackend.commuter.service.util.GtfsGeometryUtil.VehicleLocation;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Works out when a vehicle will leave each of the stops still ahead of it.
 * <p>
 * <b>The timetable is the only source of times, and it is corrected rather than trusted.</b> Samtrafiken
 * serves VehiclePositions and nothing else — there is no TripUpdates feed, so no predicted times and no
 * delay figure arrive from upstream. A raw scheduled time would therefore be wrong by exactly however late
 * the vehicle is running, which is precisely when someone is looking at the view.
 * <p>
 * The delay is instead derived from the one thing the feed does give: where the vehicle actually is. The
 * same projection that places a vehicle on the group's chain is run a second time against the vehicle's
 * <em>own</em> trip, the scheduled time at that point is interpolated, and the difference from the position
 * report's timestamp is the delay. Every remaining scheduled time is shifted by it.
 * <p>
 * Two guards keep a poor geometric match from producing a confident wrong answer: a projection further than
 * {@link #MAX_MATCH_METRES} from the trip falls back to the plain timetable, and the delay is clamped to
 * {@link #MAX_DELAY} either way. Both failures look the same from outside — a bus on a diversion and a bus
 * whose trip id points at the wrong journey both project badly — so neither is treated as an error.
 */
@Slf4j
public class GtfsPredictionUtil {
  /**
   * How far off its own trip a vehicle may project before its position stops being usable as evidence of
   * delay. Generous on purpose: the projection is onto straight lines between stops, so a genuine vehicle
   * on a bending stretch of route is legitimately some way off it.
   */
  private static final double MAX_MATCH_METRES = 1000.0;

  /** Beyond this the number says more about a bad match than about the traffic. */
  private static final Duration MAX_DELAY = Duration.ofMinutes(30);

  private GtfsPredictionUtil() {}

  /**
   * Predicted departures for the stops on {@code chain} that this vehicle has still to reach, in the order
   * it will reach them.
   * <p>
   * Empty for anything that cannot be worked out — the list is decoration on a schematic that has to keep
   * drawing, so a trip with unusable times costs its own predictions and nothing else.
   *
   * @param chain   the route group's chain, which the vehicle's {@code location} indexes into
   * @param vehicle the located vehicle, carrying its own trip and its position report
   * @param now     fallback for a position report with no usable timestamp
   */
  public static List<StopPrediction> predict(LiveTrip chain, LiveVehicle vehicle, Instant now) {
    List<GtfsStopTimeInfo> stopTimes = vehicle.getTrip().getStopTimes();
    List<GtfsStopInfo> ownStops = ownStops(stopTimes);
    if (ownStops.size() < 2) {
      return List.of();
    }
    try {
      Instant observedAt = observedAt(vehicle, now);
      LocalDate serviceDate = GtfsTimeUtil.resolveServiceDate(stopTimes, observedAt);
      VehicleLocation onOwnTrip = GtfsGeometryUtil.locateOnRoute(ownStops, vehicle.getPosition());
      Duration delay = estimateDelay(stopTimes, serviceDate, onOwnTrip, observedAt);
      Map<String, Instant> departuresAhead = departuresAhead(stopTimes, serviceDate, onOwnTrip, delay);
      return alongChain(chain, vehicle, departuresAhead);
    } catch (IllegalArgumentException e) {
      log.warn("No predictions for trip {}: {}", vehicle.getTrip().getTripId(), e.getMessage());
      return List.of();
    }
  }

  /**
   * The stops of the vehicle's own trip, as positions to project against. Empty if any stop is unresolved —
   * a gap would silently distort the geometry rather than announce itself.
   */
  private static List<GtfsStopInfo> ownStops(List<GtfsStopTimeInfo> stopTimes) {
    List<GtfsStopInfo> stops = new ArrayList<>(stopTimes.size());
    for (GtfsStopTimeInfo stopTime : stopTimes) {
      if (stopTime.getStop() == null) {
        return List.of();
      }
      stops.add(stopTime.getStop());
    }
    return stops;
  }

  /** When the vehicle reported itself, which is the moment the delay is measured at — not when we serve. */
  private static Instant observedAt(LiveVehicle vehicle, Instant now) {
    long timestamp = vehicle.getPosition().getTimestamp();
    return timestamp > 0 ? Instant.ofEpochSecond(timestamp) : now;
  }

  /**
   * How far behind its timetable the vehicle is: the gap between where it should have been at the moment it
   * reported, and when it actually reported being there. Negative when it is running early.
   */
  private static Duration estimateDelay(List<GtfsStopTimeInfo> stopTimes, LocalDate serviceDate,
                                        VehicleLocation onOwnTrip, Instant observedAt) {
    if (onOwnTrip.dist() > MAX_MATCH_METRES) {
      log.debug("Vehicle projects {} m off its own trip — falling back to the timetable", onOwnTrip.dist());
      return Duration.ZERO;
    }
    int from = GtfsTimeUtil.toSecondsSinceServiceMidnight(stopTimes.get(onOwnTrip.segIdx()).getDepartureTime());
    int to = GtfsTimeUtil.toSecondsSinceServiceMidnight(stopTimes.get(onOwnTrip.segIdx() + 1).getArrivalTime());
    int scheduledNow = (int) Math.round(from + onOwnTrip.t() * (to - from));
    Duration delay = Duration.between(GtfsTimeUtil.toInstant(serviceDate, scheduledNow), observedAt);
    return clamp(delay);
  }

  private static Duration clamp(Duration delay) {
    if (delay.compareTo(MAX_DELAY) > 0) {
      return MAX_DELAY;
    }
    if (delay.compareTo(MAX_DELAY.negated()) < 0) {
      return MAX_DELAY.negated();
    }
    return delay;
  }

  /**
   * Corrected departure times for the stops the vehicle has still to call at on its own trip, keyed by
   * parent station so the chain can be matched against them.
   * <p>
   * The first entry kept for a station wins, which only matters on a route that calls at one twice.
   */
  private static Map<String, Instant> departuresAhead(List<GtfsStopTimeInfo> stopTimes, LocalDate serviceDate,
                                                      VehicleLocation onOwnTrip, Duration delay) {
    Map<String, Instant> departures = new HashMap<>();
    for (int i = onOwnTrip.segIdx() + 1; i < stopTimes.size(); i++) {
      GtfsStopTimeInfo stopTime = stopTimes.get(i);
      Optional<GtfsStopInfo> parent = GtfsUtil.getSafeParent(stopTime.getStop());
      if (parent.isEmpty()) {
        continue;
      }
      int scheduled = GtfsTimeUtil.toSecondsSinceServiceMidnight(stopTime.getDepartureTime());
      departures.putIfAbsent(parent.get().getStopId(), GtfsTimeUtil.toInstant(serviceDate, scheduled).plus(delay));
    }
    return departures;
  }

  /**
   * Walks the drawn chain the way the vehicle is travelling and picks up a prediction wherever its own trip
   * calls at that station. A chain stop the trip skips — a short turn's stops beyond its terminus, or the
   * ones an X variant runs past — simply yields nothing, which is the normal case rather than a fault.
   */
  private static List<StopPrediction> alongChain(LiveTrip chain, LiveVehicle vehicle,
                                                 Map<String, Instant> departuresAhead) {
    List<LiveStop> stops = chain.getLiveStops();
    int segIdx = vehicle.getLocation().segIdx();
    List<StopPrediction> predictions = new ArrayList<>();
    if (vehicle.getTrip().getDirectionId() == chain.getDirection()) {
      for (int i = segIdx + 1; i < stops.size(); i++) {
        addPrediction(predictions, stops.get(i), departuresAhead);
      }
    } else {
      for (int i = segIdx; i >= 0; i--) {
        addPrediction(predictions, stops.get(i), departuresAhead);
      }
    }
    return predictions;
  }

  private static void addPrediction(List<StopPrediction> predictions, LiveStop stop,
                                    Map<String, Instant> departuresAhead) {
    Instant departure = departuresAhead.get(stop.getStopId());
    if (departure != null) {
      predictions.add(new StopPrediction(stop.getStopId(), departure));
    }
  }
}
