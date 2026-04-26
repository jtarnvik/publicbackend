package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.dao.GtfsDownloadDao;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadLog;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadStatus;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsMonitoredRoute;
import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsCalendarDateRepository;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.GtfsDataStatusResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.MonitoredRouteGroupResponse;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsMonitoredRouteRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsRouteRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsStopRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsStopTimeRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsTripRepository;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsCalendarDate;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsDataset;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsRoute;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsRouteInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsStop;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsStopTime;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsTrip;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.service.util.GtfsNameUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GtfsAccessService {
  private final GtfsDownloadDao gtfsDownloadDao;
  private final GtfsMonitoredRouteRepository gtfsMonitoredRouteRepository;
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
    validateRouteGroupConsistency(dataset.get().getMonitoredRoutes());
  }

  public void rebuildDataset() {
    Optional<GtfsDownloadLog> todayEntry = gtfsDownloadDao.findByDate(LocalDate.now());
    if (todayEntry.isPresent() && isErrorState(todayEntry.get().getStatus())) {
      log.warn("GTFS dataset not loaded — today's status is {} — live traffic unavailable", todayEntry.get().getStatus());
      dataset.set(buildEmptyDataset());
      return;
    }

    List<GtfsMonitoredRoute> monitoredRoutes = gtfsMonitoredRouteRepository.findAll();

    Map<String, GtfsRouteInfo> routeInfoById = new HashMap<>();
    for (GtfsRoute route : gtfsRouteRepository.findAll()) {
      GtfsMonitoredRoute monitoredRoute = GtfsNameUtil.findMatchingMonitoredRoute(route.getRouteShortName(), monitoredRoutes)
        .orElseThrow(() -> new IllegalStateException("No monitored route matches DB route: " + route.getRouteShortName()));
      routeInfoById.put(route.getRouteId(),
        new GtfsRouteInfo(route.getRouteId(), route.getRouteShortName(), route.getRouteLongName(), route.getRouteType(), monitoredRoute));
    }

    List<GtfsStop> allStops = gtfsStopRepository.findAll();
    Map<String, GtfsStopInfo> stopsById = new HashMap<>();
    for (GtfsStop stop : allStops) {
      if (stop.getParentStation() == null) {
        stopsById.put(stop.getStopId(), GtfsStopInfo.builder()
          .stopId(stop.getStopId())
          .stopName(stop.getStopName())
          .stopLat(stop.getStopLat())
          .stopLon(stop.getStopLon())
          .locationType(stop.getLocationType())
          .build());
      }
    }
    for (GtfsStop stop : allStops) {
      if (stop.getParentStation() != null) {
        GtfsStopInfo parent = stopsById.get(stop.getParentStation());
        if (parent == null) {
          throw new IllegalStateException(
            "Parent station not found for stop " + stop.getStopId() + ": " + stop.getParentStation());
        }
        stopsById.put(stop.getStopId(), GtfsStopInfo.builder()
          .stopId(stop.getStopId())
          .stopName(stop.getStopName())
          .stopLat(stop.getStopLat())
          .stopLon(stop.getStopLon())
          .locationType(stop.getLocationType())
          .parentStation(parent)
          .build());
      }
    }

    Map<String, List<GtfsStopTimeInfo>> stopTimesByTripId = new HashMap<>();
    for (GtfsStopTime stopTime : gtfsStopTimeRepository.findAll()) {
      GtfsStopInfo stop = stopsById.get(stopTime.getStopId());
      if (stop == null) {
        throw new IllegalStateException("Stop not found for stop_id: " + stopTime.getStopId());
      }
      GtfsStopTimeInfo info = GtfsStopTimeInfo.builder()
        .stopSequence(stopTime.getId().getStopSequence())
        .stop(stop)
        .arrivalTime(stopTime.getArrivalTime())
        .departureTime(stopTime.getDepartureTime())
        .shapeDistTraveled(stopTime.getShapeDistTraveled())
        .stopHeadsign(stopTime.getStopHeadsign())
        .build();
      stopTimesByTripId
        .computeIfAbsent(stopTime.getId().getTripId(), k -> new ArrayList<>())
        .add(info);
    }
    for (Map.Entry<String, List<GtfsStopTimeInfo>> entry : stopTimesByTripId.entrySet()) {
      entry.getValue().sort(Comparator.comparingInt(GtfsStopTimeInfo::getStopSequence));
      entry.setValue(Collections.unmodifiableList(entry.getValue()));
    }

    Map<String, GtfsTripInfo> tripInfoById = new HashMap<>();
    for (GtfsTrip trip : gtfsTripRepository.findAll()) {
      GtfsRouteInfo routeInfo = routeInfoById.get(trip.getRouteId());
      List<GtfsStopTimeInfo> stopTimes = stopTimesByTripId.getOrDefault(trip.getTripId(), List.of());
      tripInfoById.put(trip.getTripId(), GtfsTripInfo.builder()
        .tripId(trip.getTripId())
        .directionId(trip.getDirectionId())
        .serviceId(trip.getServiceId())
        .routeInfo(routeInfo)
        .stopTimes(stopTimes)
        .build());
    }

    Map<LocalDate, Set<String>> activeServiceIdsByDate = new HashMap<>();
    for (GtfsCalendarDate calendarDate : gtfsCalendarDateRepository.findAll()) {
      activeServiceIdsByDate
        .computeIfAbsent(calendarDate.getId().getServiceDate(), k -> new HashSet<>())
        .add(calendarDate.getId().getServiceId());
    }

    GtfsDataset newDataset = new GtfsDataset(monitoredRoutes, routeInfoById, tripInfoById, stopsById, stopTimesByTripId, activeServiceIdsByDate);
    dataset.set(newDataset);

    int stopTimeCount = stopTimesByTripId.values().stream().mapToInt(List::size).sum();
    log.info("GTFS dataset loaded: {} monitored routes, {} routes, {} trips, {} stops, {} stop times, {} calendar date entries",
      monitoredRoutes.size(), routeInfoById.size(), tripInfoById.size(), stopsById.size(), stopTimeCount, activeServiceIdsByDate.size());
  }

  public GtfsDataset getDataset() {
    return dataset.get();
  }

  public List<MonitoredRouteGroupResponse> getMonitoredRouteGroups() {
    List<GtfsMonitoredRoute> routes = dataset.get().getMonitoredRoutes();
    record GroupKey(TransportMode transportMode, int routeGroup) {}

    return routes.stream()
      .collect(Collectors.groupingBy(r -> new GroupKey(r.getTransportMode(), r.getRouteGroup())))
      .entrySet().stream()
      .map(entry -> {
        GroupKey key = entry.getKey();
        List<GtfsMonitoredRoute> group = entry.getValue();
        GtfsMonitoredRoute representative = group.getFirst();
        String displayName = group.stream()
          .map(GtfsMonitoredRoute::getRouteShortName)
          .sorted(Comparator.comparingInt(Integer::parseInt))
          .collect(Collectors.joining("/"));
        return MonitoredRouteGroupResponse.builder()
          .transportMode(key.transportMode().name())
          .routeGroup(key.routeGroup())
          .displayName(displayName)
          .focusStart(representative.getFocusStart())
          .focusEnd(representative.getFocusEnd())
          .onlyFocused(representative.isOnlyFocused())
          .build();
      })
      .sorted(Comparator.comparing(MonitoredRouteGroupResponse::getTransportMode)
        .thenComparingInt(MonitoredRouteGroupResponse::getRouteGroup))
      .toList();
  }

  public GtfsDataStatusResponse getDataStatus() {
    boolean staticDataAvailable = !dataset.get().getMonitoredRoutes().isEmpty();
    Optional<GtfsDownloadLog> maybeEntry = gtfsDownloadDao.findMostRecent();
    String date = maybeEntry.map(e -> e.getDate().toString()).orElse(null);
    String status = maybeEntry.map(e -> e.getStatus().name()).orElse(null);
    return new GtfsDataStatusResponse(date, status, staticDataAvailable);
  }

  void validateRouteGroupConsistency(List<GtfsMonitoredRoute> monitoredRoutes) {
    record GroupKey(TransportMode transportMode, int routeGroup) {}

    Map<GroupKey, List<GtfsMonitoredRoute>> byGroup = monitoredRoutes.stream()
      .collect(Collectors.groupingBy(r -> new GroupKey(r.getTransportMode(), r.getRouteGroup())));

    List<String> errors = new ArrayList<>();
    for (Map.Entry<GroupKey, List<GtfsMonitoredRoute>> entry : byGroup.entrySet()) {
      GroupKey key = entry.getKey();
      List<GtfsMonitoredRoute> group = entry.getValue();
      GtfsMonitoredRoute first = group.getFirst();
      for (int i = 1; i < group.size(); i++) {
        GtfsMonitoredRoute other = group.get(i);
        if (other.isOnlyFocused() != first.isOnlyFocused()) {
          errors.add(String.format("Group %s/%d: onlyFocused — %s=%b vs %s=%b",
            key.transportMode(), key.routeGroup(),
            first.getRouteShortName(), first.isOnlyFocused(),
            other.getRouteShortName(), other.isOnlyFocused()));
        }
        if (!Objects.equals(other.getFocusStart(), first.getFocusStart())) {
          errors.add(String.format("Group %s/%d: focusStart — %s=%s vs %s=%s",
            key.transportMode(), key.routeGroup(),
            first.getRouteShortName(), first.getFocusStart(),
            other.getRouteShortName(), other.getFocusStart()));
        }
        if (!Objects.equals(other.getFocusEnd(), first.getFocusEnd())) {
          errors.add(String.format("Group %s/%d: focusEnd — %s=%s vs %s=%s",
            key.transportMode(), key.routeGroup(),
            first.getRouteShortName(), first.getFocusEnd(),
            other.getRouteShortName(), other.getFocusEnd()));
        }
      }
    }

    if (!errors.isEmpty()) {
      errors.forEach(log::error);
      throw new IllegalStateException(
        "GTFS monitored route group configuration is inconsistent — application cannot start. See errors above.");
    }
  }

  private static boolean isErrorState(GtfsDownloadStatus status) {
    return status == GtfsDownloadStatus.ERROR_IN_PARSE;
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
