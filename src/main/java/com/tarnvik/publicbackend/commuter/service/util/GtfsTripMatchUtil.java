package com.tarnvik.publicbackend.commuter.service.util;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsRouteInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.TripQuery;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.util.GtfsUtil;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Decides whether a GTFS trip is the one behind a given SL departure row.
 *
 * <h2>The stop is an id join, not a name match</h2>
 * SL exposes two different stop identifiers and only one of them is foreign to GTFS. The <em>site</em> id
 * ({@code 9091001000003715}) is in SL's own namespace and cannot be converted. But every departure also
 * carries {@code stop_area.id}, and that is the national stop area number — the same number GTFS embeds in a
 * parent station id. Verified against a live feed: Skogslöparvägen 12273 → {@code 9021001012273000},
 * Kungsängen 6081 → {@code 9021001006081000}, Älvsjö 5141, Åkeshov 1241, Medborgarplatsen 1511, and every
 * one of the 7231 parent stations in the feed follows the format. So the stop leg of the match is exact.
 * <p>
 * It is still only an inferred encoding rather than a documented contract, which is why
 * {@code TripQuery.stopAreaName} exists — the caller falls back to it when the derived id is not in the
 * dataset. Cheap insurance against a stop registry that decides otherwise.
 *
 * <h2>The times agree to the second</h2>
 * SL's {@code departure.scheduled} and GTFS {@code departure_time} come out of the same SL planning system,
 * and they match exactly — 09:52:52, 10:08:18, 10:11:30 were all observed identical on both sides. So does
 * {@code departure.destination} against {@code stop_headsign}, including metro short turns. The tolerance
 * below is therefore not needed by any observed data; it is there so that a future rounding change costs a
 * second of slack rather than the whole feature.
 */
@Slf4j
public class GtfsTripMatchUtil {
  /**
   * A GTFS parent station id is {@code 9021001} + the six digit national stop area number + {@code 000}.
   */
  private static final String PARENT_STATION_ID_FORMAT = "9021001%06d000";

  /** How far the two systems' timetabled times may differ and still be taken for the same departure. */
  private static final Duration MAX_SCHEDULE_DRIFT = Duration.ofSeconds(30);

  private GtfsTripMatchUtil() {}

  /** The GTFS parent station id for an SL {@code stop_area.id}. */
  public static String toParentStationId(int stopAreaId) {
    return String.format(PARENT_STATION_ID_FORMAT, stopAreaId);
  }

  /**
   * Whether {@code trip} is the journey the query describes: the right line, calling at the right station,
   * at the right timetabled second, bound for the right place.
   *
   * @param trip            a candidate trip, normally one a live vehicle is running
   * @param query           the departure row to match
   * @param parentStationId the query's stop as a GTFS parent station id, already resolved by the caller
   * @param observedAt      when the vehicle reported itself, used to place the trip on a service day
   */
  public static boolean matches(GtfsTripInfo trip, TripQuery query, String parentStationId, Instant observedAt) {
    if (!sameLine(trip, query.getLine())) {
      return false;
    }
    List<GtfsStopTimeInfo> stopTimes = trip.getStopTimes();
    if (stopTimes == null || stopTimes.isEmpty()) {
      return false;
    }
    return callsAtStopAsScheduled(stopTimes, query, parentStationId, observedAt);
  }

  private static boolean sameLine(GtfsTripInfo trip, String designation) {
    GtfsRouteInfo routeInfo = trip.getRouteInfo();
    if (routeInfo == null) {
      return false;
    }
    return GtfsNameUtil.sameLine(routeInfo.getRouteShortName(), designation);
  }

  /**
   * Walks the trip's own stop times looking for the query's station, and checks the timetabled departure
   * there against the query. A trip may in principle call at a station twice, so every occurrence is
   * considered rather than only the first.
   */
  private static boolean callsAtStopAsScheduled(List<GtfsStopTimeInfo> stopTimes, TripQuery query,
                                                String parentStationId, Instant observedAt) {
    LocalDate serviceDate;
    try {
      serviceDate = GtfsTimeUtil.resolveServiceDate(stopTimes, observedAt);
    } catch (IllegalArgumentException e) {
      log.debug("Cannot place trip on a service day: {}", e.getMessage());
      return false;
    }
    for (GtfsStopTimeInfo stopTime : stopTimes) {
      if (!parentStationId.equals(parentIdOf(stopTime))) {
        continue;
      }
      if (!sameDestination(stopTime.getStopHeadsign(), query.getDestination())) {
        continue;
      }
      if (departsAt(stopTime, serviceDate, query.getScheduled())) {
        return true;
      }
    }
    return false;
  }

  private static String parentIdOf(GtfsStopTimeInfo stopTime) {
    if (stopTime.getStop() == null) {
      return null;
    }
    Optional<GtfsStopInfo> parent = GtfsUtil.getSafeParent(stopTime.getStop());
    return parent.map(GtfsStopInfo::getStopId).orElse(null);
  }

  private static boolean departsAt(GtfsStopTimeInfo stopTime, LocalDate serviceDate, Instant scheduled) {
    if (scheduled == null) {
      return false;
    }
    try {
      int seconds = GtfsTimeUtil.toSecondsSinceServiceMidnight(stopTime.getDepartureTime());
      Instant timetabled = GtfsTimeUtil.toInstant(serviceDate, seconds);
      return Duration.between(timetabled, scheduled).abs().compareTo(MAX_SCHEDULE_DRIFT) <= 0;
    } catch (IllegalArgumentException e) {
      log.debug("Unusable GTFS departure time: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Destination texts are written by the same operator on both sides and have matched character for
   * character in every case observed, so this only absorbs casing and stray whitespace.
   */
  private static boolean sameDestination(String headsign, String destination) {
    if (headsign == null || destination == null) {
      return false;
    }
    return normalize(headsign).equals(normalize(destination));
  }

  private static String normalize(String text) {
    return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }
}
