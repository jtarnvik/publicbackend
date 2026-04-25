package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.GtfsDataStatusResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.MonitoredRouteGroupResponse;
import com.tarnvik.publicbackend.commuter.service.GtfsAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/protected/gtfs")
@RequiredArgsConstructor
public class GtfsController {
  private final GtfsAccessService gtfsAccessService;

  @GetMapping("/route-groups")
  public ResponseEntity<List<MonitoredRouteGroupResponse>> getRouteGroups() {
    return ResponseEntity.ok(gtfsAccessService.getMonitoredRouteGroups());
  }

  @GetMapping("/status")
  public ResponseEntity<GtfsDataStatusResponse> getDataStatus() {
    return ResponseEntity.ok(gtfsAccessService.getDataStatus());
  }
}
