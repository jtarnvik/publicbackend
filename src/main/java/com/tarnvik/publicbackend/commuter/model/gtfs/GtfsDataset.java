package com.tarnvik.publicbackend.commuter.model.gtfs;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsMonitoredRoute;
import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsLiveException;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsNoRegisteredSelectorForGroupKeyException;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.GroupKey;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.LiveTrip;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.selectors.GtfsTripInfoSelector;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.selectors.GtfsTripInfoSelectorFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable in-memory snapshot of the static GTFS data for the monitored lines. Built once by
 * {@link com.tarnvik.publicbackend.commuter.service.GtfsAccessService#rebuildDataset()} and swapped
 * atomically into an {@code AtomicReference} — callers always see a fully consistent dataset,
 * never a partially populated one.
 * <p>
 * All maps are unmodifiable. The public query API will grow as the realtime feed integration
 * (C1 and later) reveals its access patterns.
 *
 * <h2>What is actually in here</h2>
 * Think of it as a filtered copy of the GTFS timetable, held in memory — only the seven lines we
 * monitor, everything else thrown away. Six buckets:
 *
 * <p><b>{@code monitoredRoutes}</b> — our own config, not GTFS data. Seven rows, one per line we track:
 * "43 is a TRAIN in group 1", "117 is a BUS in group 3". Each row also carries the focus window (which
 * stretch of the line the schematic should zoom in on). This is the list the {@code /route-groups}
 * dropdown in the frontend is built from.
 *
 * <p><b>{@code routeInfoById}</b> — the lines themselves, as SL defines them. A handful of entries:
 * line 43, line 44, 117, and so on. Each says "this is line 43, it's a commuter train". Keyed by SL's
 * route id ({@code 9011001004300000}), because that's what the timetable files reference. Each one also
 * points back at our {@code monitoredRoutes} row, so from a line you can always get to <em>our</em>
 * settings for it.
 *
 * <p><b>{@code tripInfoById}</b> — individual journeys. Not "line 43", but "the 08:24 from Bålsta to
 * Västerhaninge on a Tuesday". One entry per departure, per direction, per day-type — so this is by far
 * the biggest bucket, thousands of entries across the seven lines. Each trip knows which line it belongs
 * to, which direction, which days it runs, and its full list of stops.
 *
 * <p><b>{@code stopTimesByTripId}</b> — the stop list for each journey, in order. Given a trip, you get
 * "stop 1: Bålsta 08:24, stop 2: Bro 08:31, stop 3: Kungsängen 08:37…" all the way to the end. Each entry
 * has the arrival/departure time, which stop, and how many metres into the journey that stop sits. This is
 * the raw material the schematic is drawn from.
 *
 * <p><b>{@code stopsById}</b> — the physical places, with names and coordinates. Two kinds mixed together:
 * individual platforms ("Bålsta, spår 2") and the stations they belong to ("Bålsta"). Every platform points
 * up at its station. The schematic always uses the station, because a train going north and a train going
 * south should appear at the same dot on the map — different platforms, same place.
 *
 * <p><b>{@code activeServiceIdsByDate}</b> — the calendar. "On 2026-07-28, these service patterns run."
 * It exists so you can ask "which of the thousands of trips actually run today?" — because the timetable
 * contains every variant: weekdays, weekends, holidays, summer schedules. Nothing in the code asks that
 * question yet.
 *
 * <p>And one bucket that isn't loaded from the database but computed when the dataset is built:
 *
 * <p><b>{@code liveTrips}</b> — for each line group, one representative journey picked to stand for the
 * whole line. Line 43 has hundreds of trips a day, many of them short runs that only cover part of the
 * route. For the schematic you want a single stop chain: Bålsta → … → Västerhaninge, all 28 stations.
 * That's what this is. It's built, but nothing draws it yet.
 *
 * <h2>Finding your way back in</h2>
 * The chain, in one sentence: <b>a line has many journeys, a journey has an ordered list of stop times,
 * each stop time points at a place.</b>
 * <p>
 * Two practical ways back in:
 * <ul>
 *   <li>Start the app locally and look for the log line
 *       {@code GTFS dataset loaded: N monitored routes, N routes, N trips, N stops, N stop times, …} —
 *       that tells you the real size of each bucket today.</li>
 *   <li>{@link com.tarnvik.publicbackend.commuter.service.GtfsRealtimeService#poc()} (reachable via
 *       {@code POST /api/admin/gtfs/realtime-poc}) walks the whole chain for live bus 117 and logs it:
 *       vehicle → trip → stop chain → position. It's the cheapest way to see the data model in motion.</li>
 * </ul>
 */
@Slf4j
public class GtfsDataset {
  @Getter
  private final List<GtfsMonitoredRoute> monitoredRoutes;
  private final Map<String, GtfsRouteInfo> routeInfoById;                 // key: route_id
  private final Map<String, GtfsTripInfo> tripInfoById;                   // key: trip_id
  private final Map<String, GtfsStopInfo> stopsById;                      // key: stop_id
  private final Map<String, List<GtfsStopTimeInfo>> stopTimesByTripId;    // key: trip_id, list sorted by stop_sequence
  private final Map<LocalDate, Set<String>> activeServiceIdsByDate;       // key: service date, value: active service_ids

  private final Map<GroupKey, LiveTrip> liveTrips;

  public GtfsDataset(
    List<GtfsMonitoredRoute> monitoredRoutes,
    Map<String, GtfsRouteInfo> routeInfoById,
    Map<String, GtfsTripInfo> tripInfoById,
    Map<String, GtfsStopInfo> stopsById,
    Map<String, List<GtfsStopTimeInfo>> stopTimesByTripId,
    Map<LocalDate, Set<String>> activeServiceIdsByDate
  ) {
    this.monitoredRoutes = Collections.unmodifiableList(monitoredRoutes);
    this.routeInfoById = Collections.unmodifiableMap(routeInfoById);
    this.tripInfoById = Collections.unmodifiableMap(tripInfoById);
    this.stopsById = Collections.unmodifiableMap(stopsById);
    this.stopTimesByTripId = Collections.unmodifiableMap(stopTimesByTripId);
    this.activeServiceIdsByDate = Collections.unmodifiableMap(activeServiceIdsByDate);

    this.liveTrips = Collections.unmodifiableMap(organizeRoutes());
  }

  /**
   * Builds the canonical stop chain for every route group present in the trip data.
   * <p>
   * Each group is built independently and a failure costs only that group: the rest of the dataset, and the
   * other groups' chains, survive. This matters because the whole dataset is rebuilt from here — letting one
   * misconfigured line abort the build would take live traffic down for every line, and an unchecked
   * {@link GtfsNoRegisteredSelectorForGroupKeyException} escaping the constructor would take down the
   * dataset itself.
   * <p>
   * Both failures are configuration problems rather than data gaps, so they are logged as errors loud enough
   * to notice locally when a monitored line is added. Nothing else surfaces them until someone requests that
   * group's route data.
   */
  private Map<GroupKey, LiveTrip> organizeRoutes() {
    Map<GroupKey, List<GtfsTripInfo>> groupTrips = tripInfoById.values().stream()
      .collect(Collectors.groupingBy(GtfsTripInfo::getGroupKey));

    Map<GroupKey, LiveTrip> result = new HashMap<>();
    for (Map.Entry<GroupKey, List<GtfsTripInfo>> entry : groupTrips.entrySet()) {
      GroupKey groupKey = entry.getKey();
      try {
        GtfsTripInfoSelector selector = GtfsTripInfoSelectorFactory.findMatching(groupKey);
        result.put(groupKey, selector.select(entry.getValue()));
      } catch (GtfsNoRegisteredSelectorForGroupKeyException ex) {
        log.error("!!! NO SELECTOR REGISTERED FOR {} !!! Live traffic is unavailable for this group. "
          + "Add a GtfsTripInfoSelector subclass for it and register it in GtfsTripInfoSelectorFactory.",
          groupKey);
      } catch (GtfsLiveException ex) {
        log.error("!!! COULD NOT BUILD LIVE TRIP FOR {} !!! Live traffic is unavailable for this group. "
          + "The selector found no id trip — check its expected station count and start terminus against "
          + "the current timetable.", groupKey, ex);
      }
    }

    log.info("Live trips built for {} of {} route groups", result.size(), groupTrips.size());
    return result;
  }

  public boolean isEmpty() {
    return routeInfoById.isEmpty();
  }

  public boolean hasLiveSupport() {
    return !liveTrips.isEmpty();
  }

  public Optional<GtfsTripInfo> findTripByTripId(String tripId) {
    return Optional.ofNullable(tripInfoById.get(tripId));
  }

  public Optional<GtfsTripInfo> findTripByTripId(String tripId, TransportMode transportMode, int routeGroup) {
    Optional<GtfsTripInfo> gtfsTripInfo = Optional.ofNullable(tripInfoById.get(tripId));
    return gtfsTripInfo
      .filter(ti -> ti.getRouteInfo().getMonitoredRoute().getTransportMode() == transportMode)
      .filter(ti -> ti.getRouteInfo().getMonitoredRoute().getRouteGroup() == routeGroup);
  }

  public Optional<List<GtfsStopTimeInfo>> findStopTimesByTripId(String tripId) {
    return Optional.ofNullable(stopTimesByTripId.get(tripId));
  }

  public Optional<GtfsStopInfo> getStopByStopId(String stopId) {
    return Optional.ofNullable(stopsById.get(stopId));
  }

  /**
   * The canonical stop chain for a route group — the one every vehicle on that group is placed against.
   * Empty only when the group's selector could not find its id trip, which is a configuration problem.
   */
  public Optional<LiveTrip> findLiveTrip(GroupKey groupKey) {
    return Optional.ofNullable(liveTrips.get(groupKey));
  }

  /**
   * Any one monitored route from the group, for reading the group's configuration (focus window,
   * {@code onlyFocused}). Which row is returned does not matter — startup validation guarantees every row in
   * a group carries identical focus config.
   */
  public Optional<GtfsMonitoredRoute> findMonitoredRoute(GroupKey groupKey) {
    return monitoredRoutes.stream()
      .filter(route -> route.getGroupKey().equals(groupKey))
      .findFirst();
  }
}
