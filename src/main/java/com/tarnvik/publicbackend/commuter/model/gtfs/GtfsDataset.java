package com.tarnvik.publicbackend.commuter.model.gtfs;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsMonitoredLine;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable in-memory snapshot of the static GTFS data for the monitored lines. Built once by
 * {@link com.tarnvik.publicbackend.commuter.service.GtfsAccessService#rebuildDataset()} and swapped
 * atomically into an {@code AtomicReference} — callers always see a fully consistent dataset,
 * never a partially populated one.
 * <p>
 * All maps are unmodifiable. The public query API will grow as the realtime feed integration
 * (C1 and later) reveals its access patterns.
 */
public class GtfsDataset {
  private final List<GtfsMonitoredLine> monitoredLines;
  private final Map<String, GtfsRoute> routesById;                   // key: route_id
  private final Map<String, GtfsTrip> tripsById;                     // key: trip_id
  private final Map<String, GtfsStop> stopsById;                     // key: stop_id
  private final Map<String, List<GtfsStopTime>> stopTimesByTripId;   // key: trip_id, list sorted by stop_sequence
  private final Map<LocalDate, Set<String>> activeServiceIdsByDate;  // key: service date, value: active service_ids

  public GtfsDataset(
    List<GtfsMonitoredLine> monitoredLines,
    Map<String, GtfsRoute> routesById,
    Map<String, GtfsTrip> tripsById,
    Map<String, GtfsStop> stopsById,
    Map<String, List<GtfsStopTime>> stopTimesByTripId,
    Map<LocalDate, Set<String>> activeServiceIdsByDate
  ) {
    this.monitoredLines = Collections.unmodifiableList(monitoredLines);
    this.routesById = Collections.unmodifiableMap(routesById);
    this.tripsById = Collections.unmodifiableMap(tripsById);
    this.stopsById = Collections.unmodifiableMap(stopsById);
    this.stopTimesByTripId = Collections.unmodifiableMap(stopTimesByTripId);
    this.activeServiceIdsByDate = Collections.unmodifiableMap(activeServiceIdsByDate);
  }

  public boolean isEmpty() {
    return routesById.isEmpty();
  }

  public Optional<GtfsRoute> findRouteByTripId(String tripId) {
    GtfsTrip trip = tripsById.get(tripId);
    if (trip == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(routesById.get(trip.getRouteId()));
  }
}
