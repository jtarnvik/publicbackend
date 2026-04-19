package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.exception.GtfsDownloadException;
import com.tarnvik.publicbackend.commuter.model.domain.dao.GtfsDownloadDao;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadLog;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadStatus;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsMonitoredLine;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsMonitoredLineRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsCalendarDateRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsRouteRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsStopRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsStopTimeRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsTripRepository;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsCalendarDate;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsCalendarDateId;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsRoute;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStop;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTime;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeId;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTrip;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.pushover.PushoverProvider;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses the static GTFS feed for Storstockholms Lokaltrafik (SL) and persists a filtered subset
 * of the data to the database. The parsed data is the foundation for all vehicle tracking and
 * schedule queries in this application.
 *
 * <h2>What is GTFS?</h2>
 * GTFS (General Transit Feed Specification) is a standard format for public transit schedules.
 * Samtrafiken publishes a regional feed covering all operators in Sweden. The feed is a zip archive
 * containing a set of CSV files. This service reads the unzipped files from
 * {@code /tmp/sl-gtfs-cache/unzipped/}, which are placed there by {@link GtfsDownloadService}.
 *
 * <h2>Why filter?</h2>
 * The full regional feed covers all operators and thousands of lines. We only care about a small
 * set of monitored lines (pendeltåg 43/44, bus 117/112, metro 17/18/19), configured in the
 * {@code gtfs_monitored_line} table. The full {@code stop_times.txt} alone is ~140 MB — loading
 * and persisting the entire feed would be wasteful. Instead, each parse step filters aggressively
 * and only retains data that belongs to the monitored lines.
 *
 * <h2>Parse order and the filtering dependency chain</h2>
 * The files must be parsed in a specific order because each step produces a set of IDs that the
 * next step uses as its filter. Skipping ahead or reordering would require loading an unfiltered
 * file (too large) or re-reading an earlier file:
 * <ol>
 *   <li>{@code agency.txt} — resolves the SL {@code agency_id}. Not hardcoded because it may
 *       change between feed versions.</li>
 *   <li>{@code routes.txt} — filtered by agency + transport mode + route name pattern.
 *       Produces: {@code routeIds}</li>
 *   <li>{@code trips.txt} — filtered by {@code routeIds}.
 *       Produces: {@code tripIds} (for stop_times) and {@code serviceIds} (for calendar_dates)</li>
 *   <li>{@code stop_times.txt} — filtered by {@code tripIds}. The largest file (~90,000 rows after
 *       filtering). Produces: {@code stopIds} (the unique stops actually served by these trips)</li>
 *   <li>{@code stops.txt} — filtered by {@code stopIds}. Small (~250 rows).</li>
 *   <li>{@code calendar_dates.txt} — filtered by {@code serviceIds}. Defines which dates each
 *       service runs. Only {@code exception_type=1} (runs this date) rows are stored — type 2
 *       (cancellation overrides) are irrelevant because {@code calendar.txt} defines no base
 *       schedule in the Samtrafiken feed.</li>
 * </ol>
 * The intermediate ID sets ({@code routeIds}, {@code tripIds}, etc.) flow as local variables
 * through {@link #parseIfReady()} — no shared instance state.
 *
 * <h2>Transaction architecture</h2>
 * {@link #parseIfReady()} is the single {@code @Transactional} boundary. All file parsing,
 * deletes, and inserts run within this one transaction. On success it commits atomically —
 * concurrent readers using the old data see a consistent snapshot right up until the commit,
 * at which point all new data becomes visible at once. On failure the entire transaction rolls
 * back and the existing data is preserved intact.
 * <p>
 * Status updates ({@code PARSE_START}, {@code PARSE_DONE}, {@code FAILED}) go through
 * {@link GtfsDownloadDao} methods annotated with {@code REQUIRES_NEW}. This propagation suspends
 * the outer transaction and commits the status update independently, so the progress is always
 * visible in the database regardless of whether the parse transaction eventually rolls back.
 * <p>
 * <strong>The {@code detach(entry)} call is critical.</strong> {@code GtfsDownloadDao} methods
 * mutate the {@code GtfsDownloadLog entry} object in-place before saving it in the inner
 * {@code REQUIRES_NEW} transaction. Without detaching, the outer session continues to track
 * {@code entry} as dirty. Any subsequent {@code entityManager.flush()} call (used during
 * {@code stop_times.txt} batch inserts) would flush those dirty fields as an UPDATE on
 * {@code gtfs_download_log} within the outer transaction — acquiring an exclusive row lock.
 * When {@code markParseDone()} then tries to update the same row in its own {@code REQUIRES_NEW}
 * transaction, it is blocked by that lock for up to MySQL's {@code innodb_lock_wait_timeout}
 * (default 50 seconds), at which point it fails. Detaching removes {@code entry} from the outer
 * session entirely so the outer transaction never touches {@code gtfs_download_log}.
 *
 * <h2>Delete strategy</h2>
 * Each parse step deletes all existing rows for its table before inserting the new set.
 * {@code deleteAllInBatch()} is used throughout — it issues a single {@code DELETE FROM table}
 * SQL statement without loading entities first. {@code deleteAll()} would load every row into
 * memory before deleting, which is prohibitively slow for {@code gtfs_stop_time} (~90,000 rows).
 *
 * <h2>Batch inserts for stop_times</h2>
 * {@code stop_times.txt} produces ~90,000 rows. Without batching, Hibernate would accumulate all
 * 90,000 managed entities in the persistence context until the transaction commits, wasting heap.
 * Instead, rows are inserted in batches of {@value #STOP_TIME_BATCH_SIZE}: after each
 * {@code saveAll(batch)}, {@code entityManager.flush()} pushes the SQL to the database and
 * {@code entityManager.clear()} evicts the entities from the persistence context. This keeps heap
 * usage constant regardless of file size.
 * <p>
 * True JDBC-level batching (rewriting 500 inserts into one multi-row statement) requires
 * {@code spring.jpa.properties.hibernate.jdbc.batch_size=500} in {@code application.properties}
 * and {@code rewriteBatchedStatements=true} in the JDBC URL (MySQL only — PostgreSQL batches
 * correctly by default).
 *
 * <h2>After parsing</h2>
 * This service does not touch the in-memory {@link GtfsDataset} cache. Once
 * {@link #parseIfReady()} returns and the transaction commits, {@link GtfsPipelineService}
 * calls {@link GtfsAccessService#rebuildDataset()} to reload the newly persisted data into
 * memory. Keeping the in-memory rebuild outside this service ensures the cache is only updated
 * after the transaction has fully committed — not while it is still in progress.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GtfsParseService {
  private static final Path UNZIP_DIR = Path.of("/tmp/sl-gtfs-cache/unzipped");
  private static final String SL_AGENCY_NAME = "Storstockholms Lokaltrafik";

  private final GtfsDownloadDao gtfsDownloadDao;
  private final GtfsMonitoredLineRepository gtfsMonitoredLineRepository;
  private final GtfsRouteRepository gtfsRouteRepository;
  private final GtfsTripRepository gtfsTripRepository;
  private final GtfsStopTimeRepository gtfsStopTimeRepository;
  private final GtfsStopRepository gtfsStopRepository;
  private final GtfsCalendarDateRepository gtfsCalendarDateRepository;
  private final PushoverProvider pushoverProvider;
  private final EntityManager entityManager;

  private static final int STOP_TIME_BATCH_SIZE = 500;

  private record TripParseResult(Set<String> tripIds, Set<String> serviceIds) {}

  @Transactional
  public void parseIfReady() {
    LocalDate today = LocalDate.now();
    Optional<GtfsDownloadLog> maybeEntry = gtfsDownloadDao.findByDate(today);

    if (maybeEntry.isEmpty() || maybeEntry.get().getStatus() != GtfsDownloadStatus.UNZIP_DONE) {
      log.info("GTFS parse skipped — today's status is not UNZIP_DONE");
      return;
    }

    GtfsDownloadLog entry = maybeEntry.get();
    gtfsDownloadDao.markParseStart(entry);
    entityManager.detach(entry);

    try {
      String agencyId = parseAgencyId();
      List<GtfsMonitoredLine> monitoredLines = gtfsMonitoredLineRepository.findAll();
      Set<String> routeIds = parseRoutes(agencyId, monitoredLines);
      TripParseResult tripResult = parseTrips(routeIds);
      Set<String> stopIds = parseStopTimes(tripResult.tripIds());
      int stopCount = parseStops(stopIds);
      int calendarDateCount = parseCalendarDates(tripResult.serviceIds());
      log.info("GTFS parse complete: {} routes, {} trips, {} stops, {} calendar dates — committing to database",
        routeIds.size(), tripResult.tripIds().size(), stopCount, calendarDateCount);
      gtfsDownloadDao.markParseDone(entry);
    } catch (Exception e) {
      throw handlePipelineFailure(entry, "parse", e);
    }
  }

  private String parseAgencyId() throws IOException {
    log.info("Parsing agency.txt");
    Path agencyFile = UNZIP_DIR.resolve("agency.txt");
    try (BufferedReader reader = Files.newBufferedReader(agencyFile)) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        throw new GtfsDownloadException("agency.txt is empty", null);
      }
      Map<String, Integer> headers = indexHeaders(headerLine);
      String line;
      while ((line = reader.readLine()) != null) {
        String[] fields = splitCsvLine(line);
        String agencyName = getField(fields, headers, "agency_name");
        if (agencyName.contains(SL_AGENCY_NAME)) {
          String agencyId = getField(fields, headers, "agency_id");
          log.info("Resolved SL agency_id: {}", agencyId);
          return agencyId;
        }
      }
    }
    throw new GtfsDownloadException("No agency matching '" + SL_AGENCY_NAME + "' found in agency.txt", null);
  }

  private Set<String> parseRoutes(String agencyId, List<GtfsMonitoredLine> monitoredLines) throws IOException {
    log.info("Parsing routes.txt");
    Path routesFile = UNZIP_DIR.resolve("routes.txt");
    List<GtfsRoute> retained = new ArrayList<>();

    try (BufferedReader reader = Files.newBufferedReader(routesFile)) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        throw new GtfsDownloadException("routes.txt is empty", null);
      }
      Map<String, Integer> headers = indexHeaders(headerLine);
      String line;
      while ((line = reader.readLine()) != null) {
        String[] fields = splitCsvLine(line);
        String rowAgencyId = getField(fields, headers, "agency_id");
        if (!rowAgencyId.equals(agencyId)) {
          continue;
        }
        String routeShortName = getField(fields, headers, "route_short_name");
        String routeTypeStr = getField(fields, headers, "route_type");
        int routeType;
        try {
          routeType = Integer.parseInt(routeTypeStr);
        } catch (NumberFormatException e) {
          log.warn("Skipping route with non-numeric route_type: {}", routeTypeStr);
          continue;
        }
        if (!matchesMonitoredLine(routeShortName, routeType, monitoredLines)) {
          continue;
        }
        GtfsRoute route = new GtfsRoute();
        route.setRouteId(getField(fields, headers, "route_id"));
        route.setRouteShortName(routeShortName);
        String routeLongName = getField(fields, headers, "route_long_name");
        route.setRouteLongName(routeLongName.isBlank() ? null : routeLongName);
        route.setRouteType(routeType);
        retained.add(route);
      }
    }

    gtfsRouteRepository.deleteAllInBatch();
    gtfsRouteRepository.saveAll(retained);
    log.info("Retained {} routes from routes.txt", retained.size());

    Set<String> routeIds = new HashSet<>();
    for (GtfsRoute route : retained) {
      routeIds.add(route.getRouteId());
    }
    return routeIds;
  }

  private TripParseResult parseTrips(Set<String> routeIds) throws IOException {
    log.info("Parsing trips.txt");
    Path tripsFile = UNZIP_DIR.resolve("trips.txt");
    List<GtfsTrip> retained = new ArrayList<>();

    try (BufferedReader reader = Files.newBufferedReader(tripsFile)) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        throw new GtfsDownloadException("trips.txt is empty", null);
      }
      Map<String, Integer> headers = indexHeaders(headerLine);
      String line;
      while ((line = reader.readLine()) != null) {
        String[] fields = splitCsvLine(line);
        String routeId = getField(fields, headers, "route_id");
        if (!routeIds.contains(routeId)) {
          continue;
        }
        GtfsTrip trip = new GtfsTrip();
        trip.setTripId(getField(fields, headers, "trip_id"));
        trip.setRouteId(routeId);
        trip.setServiceId(getField(fields, headers, "service_id"));
        String directionIdStr = getField(fields, headers, "direction_id");
        try {
          trip.setDirectionId(Integer.parseInt(directionIdStr));
        } catch (NumberFormatException e) {
          log.warn("Skipping trip with non-numeric direction_id: {}", directionIdStr);
          continue;
        }
        retained.add(trip);
      }
    }

    gtfsTripRepository.deleteAllInBatch();
    gtfsTripRepository.saveAll(retained);
    log.info("Retained {} trips from trips.txt", retained.size());

    Set<String> tripIds = new HashSet<>();
    Set<String> serviceIds = new HashSet<>();
    for (GtfsTrip trip : retained) {
      tripIds.add(trip.getTripId());
      serviceIds.add(trip.getServiceId());
    }
    return new TripParseResult(tripIds, serviceIds);
  }

  private Set<String> parseStopTimes(Set<String> tripIds) throws IOException {
    log.info("Parsing stop_times.txt");
    Path stopTimesFile = UNZIP_DIR.resolve("stop_times.txt");
    Set<String> retainedStopIds = new HashSet<>();

    gtfsStopTimeRepository.deleteAllInBatch();

    List<GtfsStopTime> batch = new ArrayList<>();
    int totalCount = 0;

    try (BufferedReader reader = Files.newBufferedReader(stopTimesFile)) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        throw new GtfsDownloadException("stop_times.txt is empty", null);
      }
      Map<String, Integer> headers = indexHeaders(headerLine);
      String line;
      while ((line = reader.readLine()) != null) {
        String[] fields = splitCsvLine(line);
        String tripId = getField(fields, headers, "trip_id");
        if (!tripIds.contains(tripId)) {
          continue;
        }
        String stopSequenceStr = getField(fields, headers, "stop_sequence");
        int stopSequence;
        try {
          stopSequence = Integer.parseInt(stopSequenceStr);
        } catch (NumberFormatException e) {
          log.warn("Skipping stop_time with non-numeric stop_sequence: {}", stopSequenceStr);
          continue;
        }
        String stopId = getField(fields, headers, "stop_id");
        retainedStopIds.add(stopId);

        GtfsStopTimeId id = new GtfsStopTimeId();
        id.setTripId(tripId);
        id.setStopSequence(stopSequence);

        GtfsStopTime stopTime = new GtfsStopTime();
        stopTime.setId(id);
        stopTime.setStopId(stopId);
        stopTime.setArrivalTime(getField(fields, headers, "arrival_time"));
        stopTime.setDepartureTime(getField(fields, headers, "departure_time"));
        String distStr = getField(fields, headers, "shape_dist_traveled");
        stopTime.setShapeDistTraveled(distStr.isBlank() ? null : Double.parseDouble(distStr));
        String headsign = getField(fields, headers, "stop_headsign");
        stopTime.setStopHeadsign(headsign.isBlank() ? null : headsign);

        batch.add(stopTime);

        if (batch.size() == STOP_TIME_BATCH_SIZE) {
          gtfsStopTimeRepository.saveAll(batch);
          entityManager.flush();
          entityManager.clear();
          totalCount += batch.size();
          batch.clear();
          if (totalCount % 10_000 == 0) {
            log.info("stop_times.txt progress: {} rows written", totalCount);
          }
        }
      }
    }

    if (!batch.isEmpty()) {
      gtfsStopTimeRepository.saveAll(batch);
      entityManager.flush();
      entityManager.clear();
      totalCount += batch.size();
    }

    log.info("Retained {} stop times from stop_times.txt ({} unique stops)", totalCount, retainedStopIds.size());
    return retainedStopIds;
  }

  private int parseStops(Set<String> stopIds) throws IOException {
    log.info("Parsing stops.txt");
    Path stopsFile = UNZIP_DIR.resolve("stops.txt");
    List<GtfsStop> retained = new ArrayList<>();

    try (BufferedReader reader = Files.newBufferedReader(stopsFile)) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        throw new GtfsDownloadException("stops.txt is empty", null);
      }
      Map<String, Integer> headers = indexHeaders(headerLine);
      String line;
      while ((line = reader.readLine()) != null) {
        String[] fields = splitCsvLine(line);
        String stopId = getField(fields, headers, "stop_id");
        if (!stopIds.contains(stopId)) {
          continue;
        }
        String latStr = getField(fields, headers, "stop_lat");
        String lonStr = getField(fields, headers, "stop_lon");
        try {
          GtfsStop stop = new GtfsStop();
          stop.setStopId(stopId);
          stop.setStopName(getField(fields, headers, "stop_name"));
          stop.setStopLat(Double.parseDouble(latStr));
          stop.setStopLon(Double.parseDouble(lonStr));
          String locationTypeStr = getField(fields, headers, "location_type");
          stop.setLocationType(locationTypeStr.isBlank() ? null : Integer.parseInt(locationTypeStr));
          String parentStation = getField(fields, headers, "parent_station");
          stop.setParentStation(parentStation.isBlank() ? null : parentStation);
          retained.add(stop);
        } catch (NumberFormatException e) {
          log.warn("Skipping stop {} with unparseable coordinates: lat={}, lon={}", stopId, latStr, lonStr);
        }
      }
    }

    gtfsStopRepository.deleteAllInBatch();
    gtfsStopRepository.saveAll(retained);
    log.info("Retained {} stops from stops.txt", retained.size());
    return retained.size();
  }

  private int parseCalendarDates(Set<String> serviceIds) throws IOException {
    log.info("Parsing calendar_dates.txt");
    Path calendarDatesFile = UNZIP_DIR.resolve("calendar_dates.txt");
    List<GtfsCalendarDate> retained = new ArrayList<>();

    try (BufferedReader reader = Files.newBufferedReader(calendarDatesFile)) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        throw new GtfsDownloadException("calendar_dates.txt is empty", null);
      }
      Map<String, Integer> headers = indexHeaders(headerLine);
      String line;
      while ((line = reader.readLine()) != null) {
        String[] fields = splitCsvLine(line);
        String serviceId = getField(fields, headers, "service_id");
        if (!serviceIds.contains(serviceId)) {
          continue;
        }
        String exceptionTypeStr = getField(fields, headers, "exception_type");
        if (!"1".equals(exceptionTypeStr)) {
          continue;
        }
        String dateStr = getField(fields, headers, "date");
        LocalDate serviceDate = LocalDate.parse(dateStr, DateTimeFormatter.BASIC_ISO_DATE);

        GtfsCalendarDateId id = new GtfsCalendarDateId();
        id.setServiceId(serviceId);
        id.setServiceDate(serviceDate);

        GtfsCalendarDate calendarDate = new GtfsCalendarDate();
        calendarDate.setId(id);
        calendarDate.setExceptionType(1);
        retained.add(calendarDate);
      }
    }

    gtfsCalendarDateRepository.deleteAllInBatch();
    gtfsCalendarDateRepository.saveAll(retained);
    log.info("Retained {} calendar dates from calendar_dates.txt", retained.size());
    return retained.size();
  }

  private boolean matchesMonitoredLine(String routeShortName, int routeType, List<GtfsMonitoredLine> monitoredLines) {
    for (GtfsMonitoredLine monitored : monitoredLines) {
      if (monitored.getTransportMode().getGtfsRouteType() != routeType) {
        continue;
      }
      String base = Pattern.quote(monitored.getRouteShortName());
      if (routeShortName.matches("^" + base + "[A-Za-z]?$")) {
        return true;
      }
    }
    return false;
  }

  private GtfsDownloadException handlePipelineFailure(GtfsDownloadLog entry, String phase, Exception e) {
    log.error("GTFS {} failed: {}", phase, e.getMessage(), e);
    gtfsDownloadDao.updateFailed(entry, e.getMessage());
    pushoverProvider.sendGtfsPipelineErrorNotification(phase, e.getMessage());
    return new GtfsDownloadException("GTFS " + phase + " failed: " + e.getMessage(), e);
  }

  private Map<String, Integer> indexHeaders(String headerLine) {
    String[] headers = splitCsvLine(headerLine);
    Map<String, Integer> index = new HashMap<>();
    for (int i = 0; i < headers.length; i++) {
      index.put(headers[i].trim(), i);
    }
    return index;
  }

  private String getField(String[] fields, Map<String, Integer> headers, String name) {
    Integer idx = headers.get(name);
    if (idx == null || idx >= fields.length) {
      return "";
    }
    return fields[idx].trim();
  }

  private String[] splitCsvLine(String line) {
    List<String> fields = new ArrayList<>();
    boolean inQuotes = false;
    StringBuilder current = new StringBuilder();
    for (char c : line.toCharArray()) {
      if (c == '"') {
        inQuotes = !inQuotes;
      } else if (c == ',' && !inQuotes) {
        fields.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    fields.add(current.toString());
    return fields.toArray(new String[0]);
  }
}
