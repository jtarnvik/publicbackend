package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * One departure row from SL's departures API, expressed as everything needed to find the GTFS trip behind it.
 * <p>
 * <b>Why a content key rather than an id.</b> SL's {@code journey.id} is a number in SL's own namespace and
 * has no counterpart in GTFS, whose {@code trip_id} is a string like {@code 14010000656749468}. The two
 * systems are joined instead on what they both describe: the same vehicle, leaving the same station, at the
 * same timetabled second, bound for the same place.
 * <p>
 * The key is four parts and every one is load-bearing. Line alone is not enough because a route group holds
 * several lines (43 and 44; 17, 18 and 19). Station and time alone are not enough either — at Älvsjö two
 * line 43 departures leave at exactly 10:00:00, one for Västerhaninge and one for Kungsängen — so the
 * destination is what separates the two directions.
 *
 * @see com.tarnvik.publicbackend.commuter.service.util.GtfsTripMatchUtil
 */
@Value
@Builder
public class TripQuery {
  TransportMode transportMode;
  int routeGroup;

  /** SL's line designation, e.g. {@code "117"} or {@code "43X"}. Variant suffixes are matched loosely. */
  String line;

  /**
   * SL's {@code stop_area.id}, which is the national stop area number and therefore convertible straight to
   * a GTFS parent station id — see {@link com.tarnvik.publicbackend.commuter.service.util.GtfsTripMatchUtil}.
   */
  int stopAreaId;

  /** SL's {@code stop_area.name}, used only if the derived parent station id is not in the dataset. */
  String stopAreaName;

  /** SL's {@code departure.scheduled} — the timetabled time, not the realtime prediction. */
  Instant scheduled;

  /** SL's {@code departure.destination}, compared against the trip's {@code stop_headsign}. */
  String destination;
}
