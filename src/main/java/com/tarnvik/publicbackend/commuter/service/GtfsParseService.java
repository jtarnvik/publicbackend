package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.exception.GtfsDownloadException;
import com.tarnvik.publicbackend.commuter.model.domain.dao.GtfsDownloadDao;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadLog;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadStatus;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsMonitoredLine;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsMonitoredLineRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsRouteRepository;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsRoute;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.pushover.PushoverProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class GtfsParseService {
  private static final Path UNZIP_DIR = Path.of("/tmp/sl-gtfs-cache/unzipped");
  private static final String SL_AGENCY_NAME = "Storstockholms Lokaltrafik";

  private final GtfsDownloadDao gtfsDownloadDao;
  private final GtfsMonitoredLineRepository gtfsMonitoredLineRepository;
  private final GtfsRouteRepository gtfsRouteRepository;
  private final PushoverProvider pushoverProvider;

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

    try {
      String agencyId = parseAgencyId();
      List<GtfsMonitoredLine> monitoredLines = gtfsMonitoredLineRepository.findAll();
      Set<String> routeIds = parseRoutes(agencyId, monitoredLines);
      log.info("GTFS parse complete: {} routes retained", routeIds.size());
      gtfsDownloadDao.markParseDone(entry);
    } catch (Exception e) {
      throw handlePipelineFailure(entry, "parse", e);
    }
  }

  private String parseAgencyId() throws IOException {
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

    gtfsRouteRepository.deleteAll();
    gtfsRouteRepository.saveAll(retained);
    log.info("Stored {} routes in gtfs_route", retained.size());

    Set<String> routeIds = new HashSet<>();
    for (GtfsRoute route : retained) {
      routeIds.add(route.getRouteId());
    }
    return routeIds;
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
