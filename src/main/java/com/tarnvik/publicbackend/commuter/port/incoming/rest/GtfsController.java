package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.ResolvedTrip;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.RouteData;
import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.TripQuery;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.GtfsDataStatusResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.MonitoredRouteGroupResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.ResolveTripResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.RouteDataResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.RouteGroupStopsResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.mapper.RouteDataMapper;
import com.tarnvik.publicbackend.commuter.service.GtfsAccessService;
import com.tarnvik.publicbackend.commuter.service.GtfsRealtimeService;
import com.tarnvik.publicbackend.commuter.service.util.GtfsTimeUtil;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
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

  /**
   * Finds the live vehicle behind one departure row, so that clicking it can open the schematic with that
   * vehicle already selected.
   * <p>
   * The parameters are a content key rather than an id, because SL's {@code journey.id} has no counterpart
   * in GTFS — see {@link com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.TripQuery}. They are the
   * departure row's own fields: {@code line.designation}, {@code stop_area.id} and {@code .name},
   * {@code scheduled}, {@code destination}.
   * <p>
   * {@code scheduled} is a {@link LocalDateTime} rather than an {@code Instant} because SL sends it without
   * an offset — {@code 2026-08-07T10:21:01}. Passing SL's field through untouched keeps the caller from
   * having to know which timezone it is in; {@link GtfsTimeUtil} already does.
   * <p>
   * Always 200. Not finding a vehicle is an outcome, not a failure.
   */
  @GetMapping("/resolve-trip")
  public ResponseEntity<ResolveTripResponse> resolveTrip(
      @RequestParam TransportMode transportMode,
      @RequestParam int routeGroup,
      @RequestParam @NotBlank String line,
      @RequestParam int stopAreaId,
      @RequestParam @NotBlank String stopAreaName,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduled,
      @RequestParam @NotBlank String destination) {
    TripQuery query = TripQuery.builder()
      .transportMode(transportMode)
      .routeGroup(routeGroup)
      .line(line)
      .stopAreaId(stopAreaId)
      .stopAreaName(stopAreaName)
      .scheduled(GtfsTimeUtil.toInstant(scheduled))
      .destination(destination)
      .build();
    ResolvedTrip resolved = gtfsRealtimeService.resolveTrip(query);
    return ResponseEntity.ok(ResolveTripResponse.builder()
      .outcome(resolved.outcome())
      .tripId(resolved.tripId())
      .vehicleId(resolved.vehicleId())
      .build());
  }
}
