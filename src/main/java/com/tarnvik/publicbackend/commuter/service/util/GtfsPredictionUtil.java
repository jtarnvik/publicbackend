package com.tarnvik.publicbackend.commuter.service.util;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripUpdate;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.DelaySource;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveStop;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveTrip;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveVehicle;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.StopPrediction;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.VehiclePrediction;
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
 * <b>The timetable is the only source of times, and it is corrected rather than trusted.</b> Nothing
 * upstream publishes a predicted time: Samtrafiken's TripUpdates feed exists, but it reports how late a
 * vehicle <em>was</em> at platforms it has already passed and restates the raw timetable for every stop
 * ahead. A scheduled time served as-is would therefore be wrong by exactly however late the vehicle is
 * running, which is precisely when someone is looking at the view.
 * <p>
 * <b>Two sources of that offset, in order.</b> SL's own measurement at the last platform the vehicle passed
 * ({@link GtfsTripUpdate}) is preferred wherever there is one: it is recorded rather than inferred, so it
 * carries neither the projection's mid-segment bias nor its distance guard. The geometry below is the
 * fallback, and it is needed — roughly one vehicle in ten has only just left its origin and has no passed
 * platform to report.
 * <p>
 * The geometric delay is derived from the one thing the positions feed does give: where the vehicle actually
 * is. The same projection that places a vehicle on the group's chain is run a second time against the
 * vehicle's <em>own</em> trip, the scheduled time at that point is interpolated, and the difference from the
 * moment the vehicle was at that position is the delay. Every remaining scheduled time is shifted by it.
 * <p>
 * That moment is supplied by the caller and is <b>not</b> the same thing as the report's timestamp — see
 * {@code GtfsPositionAnchors}, which exists because the train feed refreshes timestamps against positions
 * it has not updated, and every second of that gap would otherwise be read as delay.
 * <p>
 * Two guards keep a poor geometric match from producing a confident wrong answer: a projection further than
 * {@link #MAX_MATCH_METRES} from the trip yields <em>no</em> geometric delay at all and the plain timetable,
 * and a delay from either source is clamped to {@link #MAX_DELAY}. Both failures look the same from outside — a bus
 * on a diversion and a bus whose trip id points at the wrong journey both project badly — so neither is
 * treated as an error.
 * <p>
 * <b>An unusable projection produces a null delay, not a zero one.</b> The two were the same value until the
 * delay started being reported rather than merely applied, and they mean opposite things: a measured zero
 * says the vehicle is on time, an absent one says nothing is known. Anything summarising a line would read a
 * run of the second kind as a punctual network.
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
   * How late this vehicle is running, and when it is due to leave each stop on {@code chain} it has still to
   * reach, in the order it will reach them.
   * <p>
   * {@link VehiclePrediction#none()} for anything that cannot be worked out — the times are decoration on a
   * schematic that has to keep drawing, so a trip with unusable stops costs its own predictions and nothing
   * else.
   *
   * @param chain      the route group's chain, which the vehicle's {@code location} indexes into
   * @param vehicle    the located vehicle, carrying its own trip and its position report
   * @param observedAt when the vehicle was at the position it is reporting — the anchored moment, not the
   *                   report's own timestamp
   * @param tripUpdate what SL has measured about this trip, or null when the feed has nothing on it
   */
  public static VehiclePrediction predict(LiveTrip chain, LiveVehicle vehicle, Instant observedAt,
                                          GtfsTripUpdate tripUpdate) {
    List<GtfsStopTimeInfo> stopTimes = vehicle.getTrip().getStopTimes();
    List<GtfsStopInfo> ownStops = ownStops(stopTimes);
    if (ownStops.size() < 2) {
      return VehiclePrediction.none();
    }
    try {
      LocalDate serviceDate = GtfsTimeUtil.resolveServiceDate(stopTimes, observedAt);
      VehicleLocation onOwnTrip = GtfsGeometryUtil.locateOnRoute(ownStops, vehicle.getPosition());
      DelaySource source = DelaySource.TRIP_UPDATES;
      Duration delay = measuredDelay(stopTimes, onOwnTrip, tripUpdate);
      if (delay == null) {
        source = DelaySource.GEOMETRY;
        delay = estimateDelay(stopTimes, serviceDate, onOwnTrip, observedAt);
      }
      Map<String, Instant> departuresAhead = departuresAhead(stopTimes, serviceDate, onOwnTrip, delay);
      List<StopPrediction> stopPredictions = alongChain(chain, vehicle, departuresAhead);
      if (delay == null) {
        return VehiclePrediction.uncorrected(stopPredictions);
      }
      return new VehiclePrediction(delay, source, stopPredictions);
    } catch (IllegalArgumentException e) {
      log.warn("No predictions for trip {}: {}", vehicle.getTrip().getTripId(), e.getMessage());
      return VehiclePrediction.none();
    }
  }

  /**
   * SL's own delay at the last platform this vehicle passed, or null when the feed has nothing to say about
   * the trip yet.
   * <p>
   * <b>The geometry decides which measurement is current, not the clock.</b> A trip update's rows carry no
   * marker for where the vehicle has got to, and the rows ahead of it hold the raw timetable — whose times
   * have, for a late vehicle, often already passed. Choosing by "everything timed before now" would
   * therefore admit a zero from a stop ahead precisely for the vehicles that are late, and report them
   * punctual. The projected segment gives the boundary directly instead.
   * <p>
   * No distance guard here, unlike {@link #estimateDelay}: that guard exists because interpolating a
   * scheduled time along a chord far from the track is meaningless, whereas asking which stops a vehicle
   * has passed survives a poor projection easily. The worst a bad match can do here is pick a measurement
   * a stop or two stale — still SL's own reading of this trip, which is a far milder error than a
   * fabricated zero.
   */
  private static Duration measuredDelay(List<GtfsStopTimeInfo> stopTimes, VehicleLocation onOwnTrip,
                                        GtfsTripUpdate tripUpdate) {
    if (tripUpdate == null) {
      return null;
    }
    int passedSequence = stopTimes.get(onOwnTrip.segIdx()).getStopSequence();
    return tripUpdate.delayAtOrBefore(passedSequence).map(GtfsPredictionUtil::clamp).orElse(null);
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

  /**
   * How far behind its timetable the vehicle is: the gap between where it should have been at the moment it
   * reported, and when it actually reported being there. Negative when it is running early, and null when
   * the projection is too poor to say anything at all.
   */
  private static Duration estimateDelay(List<GtfsStopTimeInfo> stopTimes, LocalDate serviceDate,
                                        VehicleLocation onOwnTrip, Instant observedAt) {
    if (onOwnTrip.dist() > MAX_MATCH_METRES) {
      log.debug("Vehicle projects {} m off its own trip — falling back to the timetable", onOwnTrip.dist());
      return null;
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
   * A null {@code delay} means there was nothing to correct with, so the timetable is served as it stands.
   * <p>
   * The first entry kept for a station wins, which only matters on a route that calls at one twice.
   */
  private static Map<String, Instant> departuresAhead(List<GtfsStopTimeInfo> stopTimes, LocalDate serviceDate,
                                                      VehicleLocation onOwnTrip, Duration delay) {
    Duration correction = delay == null ? Duration.ZERO : delay;
    Map<String, Instant> departures = new HashMap<>();
    for (int i = onOwnTrip.segIdx() + 1; i < stopTimes.size(); i++) {
      GtfsStopTimeInfo stopTime = stopTimes.get(i);
      Optional<GtfsStopInfo> parent = GtfsUtil.getSafeParent(stopTime.getStop());
      if (parent.isEmpty()) {
        continue;
      }
      int scheduled = GtfsTimeUtil.toSecondsSinceServiceMidnight(stopTime.getDepartureTime());
      departures.putIfAbsent(parent.get().getStopId(),
        GtfsTimeUtil.toInstant(serviceDate, scheduled).plus(correction));
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
