package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsDataset;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsRouteInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsTripInfo;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsVehiclePosition;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.RouteDataResponse;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.samtrafiken.SamtrafikenProvider;
import com.tarnvik.publicbackend.commuter.service.util.GtfsGeometryUtil;
import com.tarnvik.publicbackend.commuter.service.util.GtfsGeometryUtil.VehicleLocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GtfsRealtimeService {
  private final SamtrafikenProvider samtrafikenProvider;
  private final GtfsAccessService gtfsAccessService; // will be used when POC expands
  private final GtfsRealtimeCache gtfsCache = new GtfsRealtimeCache();

  public GtfsRealtimeService(SamtrafikenProvider samtrafikenProvider, GtfsAccessService gtfsAccessService) {
    this.samtrafikenProvider = samtrafikenProvider;
    this.gtfsAccessService = gtfsAccessService;
  }

  public RouteDataResponse getRouteData(TransportMode transportMode, int routeGroup, boolean focused) {
    try {
      final GtfsDataset dataset = gtfsAccessService.getDataset();
      List<GtfsVehiclePosition> gtfsVehiclePositions = samtrafikenProvider.fetchVehiclePositions();
      log.info("Total number of vehicles {}", gtfsVehiclePositions.size());

      List<GtfsVehiclePosition> monitoredRouteVP = new ArrayList<>();
      gtfsVehiclePositions.forEach(vp -> {
        Optional<GtfsTripInfo> tripByTripId = dataset.findTripByTripId(vp.getTripId(), transportMode, routeGroup);
        if (tripByTripId.isPresent()) {
          monitoredRouteVP.add(vp);
        }
      });
      log.info("Total number of monitored VP {}", monitoredRouteVP.size());

      return RouteDataResponse.builder().status("OK").build();
    } catch (Exception e) {
      return RouteDataResponse.builder().status("Failure: " + e.getMessage()).build();
    }
  }

  // Remember TTL and all that
  // Create Line Combiner

  public void poc() {
    try {
      Optional<Map<GtfsRouteInfo, List<GtfsVehiclePosition>>> direct = gtfsCache.getDirect();
      if (direct.isEmpty()) {
        log.info("No static data, try again tomorrow.");
        return;
      }
      Map<GtfsRouteInfo, List<GtfsVehiclePosition>> vpByRoute = direct.get();
      final GtfsDataset dataset = gtfsAccessService.getDataset();

      vpByRoute.entrySet().stream()
        .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(GtfsRouteInfo::getRouteShortName)))
        .forEach(e -> log.info("  line {} -> {} vehicles", e.getKey().getRouteShortName(), e.getValue().size()));

      Optional<GtfsRouteInfo> rt117Opt = vpByRoute.keySet().stream()
        .filter(k -> k.getRouteShortName().equals("117"))
        .findFirst();
      rt117Opt.ifPresent(rt117 -> {
        List<GtfsVehiclePosition> vps117 = vpByRoute.get(rt117Opt.get());
        log.info("Found 117 line, {} vehicles", vps117.size());
        vps117.forEach(vp -> {
          GtfsTripInfo gtfsTripInfo = dataset.findTripByTripId(vp.getTripId()).orElseThrow();

          log.info("117 Vehicle: routeid: {}, tripid: {}, serviceId: {}, direction: {}",
            gtfsTripInfo.getRouteInfo().getRouteId(),
            gtfsTripInfo.getTripId(),
            gtfsTripInfo.getServiceId(),
            gtfsTripInfo.getDirectionId());

          List<GtfsStopTimeInfo> gtfsStopTimes = dataset.findStopTimesByTripId(vp.getTripId()).orElseThrow();
          log.info("Found {} stop times", gtfsStopTimes.size());
          StringBuffer buf = new StringBuffer("Stop chain mot ");
          buf.append(gtfsStopTimes.get(0).getStopHeadsign());
          buf.append(": ");
          String chain = gtfsStopTimes.stream()
            .map(st -> {
              GtfsStopInfo stop = st.getStop();
              GtfsStopInfo parent = stop.getParentStation();
              return stop.getStopName() + "/" + stop.getStopId() + "/" + parent.getStopId() + "/" + parent.getStopName();
            })
            .collect(Collectors.joining(" -> "));
          buf.append(chain);
          log.info(buf.toString());

          List<GtfsStopInfo> gtfsStops = gtfsStopTimes.stream()
            .map(GtfsStopTimeInfo::getStop)
            .toList();

          VehicleLocation vehicleLocation = GtfsGeometryUtil.locateOnRoute(gtfsStops, vp);
          log.info("Postition: {}", vehicleLocation);
        });
      });
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private class GtfsRealtimeCache {
    public Optional<Map<GtfsRouteInfo, List<GtfsVehiclePosition>>> getContinously() {
      // Fetch data
      // Start loop to fetch data every X sec (make a testrun and see the normal access time)
      // The loop should run for 5 min, during the loop any requests should return the last value, so return at immediatly.
      throw new RuntimeException("Not implemented");
    }

    public Optional<Map<GtfsRouteInfo, List<GtfsVehiclePosition>>> getDirect() {
      try {
        final GtfsDataset dataset = gtfsAccessService.getDataset();
        if (dataset.isEmpty()) {
          log.info("No static data, try again later!");
          return Optional.empty();
        }
        List<GtfsVehiclePosition> gtfsVehiclePositions = samtrafikenProvider.fetchVehiclePositions();
        log.info("Total number of vehicles {}", gtfsVehiclePositions.size());

        List<GtfsVehiclePosition> monitoredRouteVP = new ArrayList<>();
        gtfsVehiclePositions.forEach(vp -> {
          Optional<GtfsTripInfo> tripByTripId = dataset.findTripByTripId(vp.getTripId());
          if (tripByTripId.isPresent()) {
            monitoredRouteVP.add(vp);
          }
        });
        log.info("Total number of monitored line VP {}", monitoredRouteVP.size());

        Map<GtfsRouteInfo, List<GtfsVehiclePosition>> vpByRoute = new HashMap<>();
        monitoredRouteVP.forEach(vp -> {
          GtfsTripInfo gtfsTripInfo = dataset.findTripByTripId(vp.getTripId()).orElseThrow();
          vpByRoute.computeIfAbsent(gtfsTripInfo.getRouteInfo(), routeInfo -> new ArrayList<>()).add(vp);
        });
        return Optional.of(vpByRoute);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
