package com.tarnvik.publicbackend.commuter.model.gtfs;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsMonitoredRoute;
import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsEmptyTripException;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsLiveException;
import com.tarnvik.publicbackend.commuter.model.gtfs.exception.GtfsNoStopInfoException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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

  @Data
  @AllArgsConstructor
  private static class LiveTrip {
    private int direction;
    private String stopHeading;
    private final List<LiveStop> liveStops;

    public LiveTrip(GtfsTripInfo firstTrip) throws GtfsLiveException {
      if (firstTrip.getStopTimes().isEmpty()) {
        throw new GtfsEmptyTripException();
      }
      this.stopHeading = firstTrip.getStopTimes().getFirst().getStopHeadsign();
      this.direction = firstTrip.getDirectionId();
      Double distSoFar = 0.0;
      this.liveStops = new ArrayList<>();
      List<GtfsStopTimeInfo> stopTimes = firstTrip.getStopTimes();
      for (GtfsStopTimeInfo sti : stopTimes) {
        LiveStop liveStop = new LiveStop(sti, distSoFar);
        distSoFar = liveStop.getShapeDistTraveled();
        this.liveStops.add(liveStop);
      }
    }

    public void updateWith(GtfsTripInfo nextTrip) {
//      if (direction != nextTrip.getDirectionId()) {
//        log.info("Updater traverses in oppsite direction!");
//      } else {
//        log.info("Updater traverses in same    direction!");
//      }
      if (liveStops.size() != nextTrip.getStopTimes().size()) {
        log.info("Different trip length!");
      }

    }
  }

  @Data
  @ToString(onlyExplicitlyIncluded = true)
  private static class LiveStop implements GeoPosition {
    @ToString.Include
    private final String stopId;
    @ToString.Include
    private final String stopName;
    @ToString.Include
    private final Double shapeDistTraveled;
    @ToString.Include
    private final Double shapeDistTraveledSinceLast;
    private final double stopLat;
    private final double stopLon;

    public LiveStop(GtfsStopTimeInfo sti, Double distSoFar) throws GtfsNoStopInfoException {
      this.shapeDistTraveled = sti.getShapeDistTraveled();
      this.shapeDistTraveledSinceLast = sti.getShapeDistTraveled() - distSoFar;

      GtfsStopInfo posSrc = sti.getStop();
      if (posSrc == null) {
        throw new GtfsNoStopInfoException() ;
      }
      if (posSrc.hasParentStation()) {
        posSrc = posSrc.getParentStation();
      }
      this.stopId = posSrc.getStopId();
      this.stopName = posSrc.getStopName();
      this.stopLat = posSrc.getStopLat();
      this.stopLon = posSrc.getStopLon();
    }

    @Override
    public double getLat() {
      return stopLat;
    }

    @Override
    public double getLng() {
      return stopLon;
    }

  }

  private void organizeRoutes() throws GtfsLiveException {
    record GroupKey(TransportMode transportMode, int routeGroup) {
    }

    Map<GroupKey, List<GtfsMonitoredRoute>> byGroup = monitoredRoutes.stream()
      .collect(Collectors.groupingBy(r -> new GroupKey(r.getTransportMode(), r.getRouteGroup())));

    Map<GroupKey, List<GtfsTripInfo>> groupTrips = tripInfoById.values().stream()
      .filter(trip -> byGroup.containsKey(new GroupKey(trip.getRouteInfo().getMonitoredRoute().getTransportMode(), trip.getRouteInfo().getMonitoredRoute().getRouteGroup())))
      .collect(Collectors.groupingBy(trip -> new GroupKey(trip.getRouteInfo().getMonitoredRoute().getTransportMode(), trip.getRouteInfo().getMonitoredRoute().getRouteGroup())));

    for (Map.Entry<GroupKey, List<GtfsTripInfo>> entry : groupTrips.entrySet()) {
      GroupKey group = entry.getKey();
      List<GtfsTripInfo> trips = new ArrayList<>(entry.getValue());  // Need it mutable
      int idx = 0;
      int nofStops = -1;
      for (int i = 0; i < trips.size(); i++) {
        int nofStopsCurrent = trips.get(i).getStopTimes().size();
        if (nofStopsCurrent > nofStops) {
          idx = i;
          nofStops = nofStopsCurrent;
        }
      }
      GtfsTripInfo maxLengthTrip = trips.remove(idx);
      LiveTrip trip = new LiveTrip(maxLengthTrip);
      log.info("Group: {}, LiveTrip: {}", group, trip);

      for (int i = 1; i < trips.size(); ++i) {
        GtfsTripInfo next = trips.get(i);
        trip.updateWith(next);

      }
    }
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
