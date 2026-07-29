package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.RouteData;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.GtfsDataStatusResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.MonitoredRouteGroupResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.RouteDataResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.RouteGroupStopsResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.mapper.RouteDataMapper;
import com.tarnvik.publicbackend.commuter.service.GtfsAccessService;
import com.tarnvik.publicbackend.commuter.service.GtfsRealtimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/protected/gtfs")
@RequiredArgsConstructor
public class GtfsController {
  private final GtfsAccessService gtfsAccessService;
  private final GtfsRealtimeService gtfsRealtimeService;
  private final RouteDataMapper routeDataMapper;

  @GetMapping("/route-groups")
  public ResponseEntity<List<MonitoredRouteGroupResponse>> getRouteGroups() {
    return ResponseEntity.ok(gtfsAccessService.getMonitoredRouteGroups());
  }

  @GetMapping("/status")
  public ResponseEntity<GtfsDataStatusResponse> getDataStatus() {
    return ResponseEntity.ok(gtfsAccessService.getDataStatus());
  }

  /**
   * Every stop of every route group, for the favourite stop picker in the settings dialog. Empty when the
   * dataset is not loaded — the dialog must still open and save in that state.
   */
  @GetMapping("/route-group-stops")
  public ResponseEntity<List<RouteGroupStopsResponse>> getRouteGroupStops() {
    return ResponseEntity.ok(gtfsAccessService.getRouteGroupStops());
  }

  @GetMapping("/route-data")
  public ResponseEntity<RouteDataResponse> getRouteData(
      @RequestParam TransportMode transportMode,
      @RequestParam int routeGroup,
      @RequestParam boolean focused) {
    RouteData routeData = gtfsRealtimeService.getRouteData(transportMode, routeGroup, focused);
    return ResponseEntity.ok(routeDataMapper.toResponse(routeData));
  }
}
