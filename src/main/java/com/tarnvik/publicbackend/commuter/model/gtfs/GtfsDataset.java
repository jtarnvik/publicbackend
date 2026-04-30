package com.tarnvik.publicbackend.commuter.model.gtfs;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsMonitoredRoute;
import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsLiveException;
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
 */
@Slf4j
public class GtfsDataset {
  @Getter
  private final List<GtfsMonitoredRoute> monitoredRoutes;
  private final Map<String, GtfsRouteInfo> routeInfoById;             // key: route_id
  private final Map<String, GtfsTripInfo> tripInfoById;               // key: trip_id
  private final Map<String, GtfsStopInfo> stopsById;                      // key: stop_id
  private final Map<String, List<GtfsStopTimeInfo>> stopTimesByTripId;    // key: trip_id, list sorted by stop_sequence
  private final Map<LocalDate, Set<String>> activeServiceIdsByDate;   // key: service date, value: active service_ids

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

//    try {
//      organizeRoutes();
//    } catch (GtfsLiveException ex) {
//      log.warn("Error while parsing GTFS dataset: " + ex.getMessage());
//    }
  }

  private Map<GroupKey, LiveTrip> organizeRoutes() throws GtfsLiveException {
//    Map<GroupKey, List<GtfsMonitoredRoute>> byGroup = monitoredRoutes.stream()
//      .collect(Collectors.groupingBy(GtfsMonitoredRoute::getGroupKey));

    Map<GroupKey, List<GtfsTripInfo>> groupTrips = tripInfoById.values().stream()
//      .filter(trip -> byGroup.containsKey(trip.getGroupKey()))
      .collect(Collectors.groupingBy(GtfsTripInfo::getGroupKey));

    Map<GroupKey, LiveTrip> result = new HashMap<>();
    for (Map.Entry<GroupKey, List<GtfsTripInfo>> entry : groupTrips.entrySet()) {
      GroupKey groupKey = entry.getKey();
      List<GtfsTripInfo> trips = entry.getValue();

      GtfsTripInfoSelector selector = GtfsTripInfoSelectorFactory.findMatching(groupKey);
      LiveTrip liveTrip = selector.select(trips);
      result.put(groupKey, liveTrip);
    }

    return result;
  }

  public boolean isEmpty() {
    return routeInfoById.isEmpty();
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
}
