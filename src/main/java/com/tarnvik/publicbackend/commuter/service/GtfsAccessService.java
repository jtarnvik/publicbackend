package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsMonitoredLine;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsCalendarDateRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsMonitoredLineRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsRouteRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsStopRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsStopTimeRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsTripRepository;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsCalendarDate;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsDataset;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsRoute;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStop;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTime;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTrip;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class GtfsAccessService {
  private final GtfsMonitoredLineRepository gtfsMonitoredLineRepository;
  private final GtfsRouteRepository gtfsRouteRepository;
  private final GtfsTripRepository gtfsTripRepository;
  private final GtfsStopRepository gtfsStopRepository;
  private final GtfsStopTimeRepository gtfsStopTimeRepository;
  private final GtfsCalendarDateRepository gtfsCalendarDateRepository;

  private final AtomicReference<GtfsDataset> dataset = new AtomicReference<>(buildEmptyDataset());

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    log.info("Application ready — loading GTFS dataset from database");
    rebuildDataset();
  }

  public void rebuildDataset() {
    List<GtfsMonitoredLine> monitoredLines = gtfsMonitoredLineRepository.findAll();

    Map<String, GtfsRoute> routesById = new HashMap<>();
    for (GtfsRoute route : gtfsRouteRepository.findAll()) {
      routesById.put(route.getRouteId(), route);
    }

    Map<String, GtfsTripInfo> tripInfoById = new HashMap<>();
    for (GtfsTrip trip : gtfsTripRepository.findAll()) {
      GtfsRoute route = routesById.get(trip.getRouteId());
      tripInfoById.put(trip.getTripId(), new GtfsTripInfo(trip.getTripId(), trip.getDirectionId(), trip.getServiceId(), route));
    }

    Map<String, GtfsStop> stopsById = new HashMap<>();
    for (GtfsStop stop : gtfsStopRepository.findAll()) {
      stopsById.put(stop.getStopId(), stop);
    }

    Map<String, List<GtfsStopTime>> stopTimesByTripId = new HashMap<>();
    for (GtfsStopTime stopTime : gtfsStopTimeRepository.findAll()) {
      stopTimesByTripId
        .computeIfAbsent(stopTime.getId().getTripId(), k -> new ArrayList<>())
        .add(stopTime);
    }
    for (List<GtfsStopTime> stopTimes : stopTimesByTripId.values()) {
      stopTimes.sort((a, b) -> Integer.compare(a.getId().getStopSequence(), b.getId().getStopSequence()));
    }

    Map<LocalDate, Set<String>> activeServiceIdsByDate = new HashMap<>();
    for (GtfsCalendarDate calendarDate : gtfsCalendarDateRepository.findAll()) {
      activeServiceIdsByDate
        .computeIfAbsent(calendarDate.getId().getServiceDate(), k -> new HashSet<>())
        .add(calendarDate.getId().getServiceId());
    }

    GtfsDataset newDataset = new GtfsDataset(monitoredLines, routesById, tripInfoById, stopsById, stopTimesByTripId, activeServiceIdsByDate);
    dataset.set(newDataset);

    int stopTimeCount = stopTimesByTripId.values().stream().mapToInt(List::size).sum();
    log.info("GTFS dataset loaded: {} monitored lines, {} routes, {} trips, {} stops, {} stop times, {} calendar date entries",
      monitoredLines.size(), routesById.size(), tripInfoById.size(), stopsById.size(), stopTimeCount, activeServiceIdsByDate.size());
  }

  public GtfsDataset getDataset() {
    return dataset.get();
  }

  private static GtfsDataset buildEmptyDataset() {
    return new GtfsDataset(
      Collections.emptyList(),
      Collections.emptyMap(),
      Collections.emptyMap(),
      Collections.emptyMap(),
      Collections.emptyMap(),
      Collections.emptyMap()
    );
  }
}
