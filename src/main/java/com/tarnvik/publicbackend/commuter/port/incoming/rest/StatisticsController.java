package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.model.domain.entity.StatName;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.StatisticsResponse;
import com.tarnvik.publicbackend.commuter.service.AllowedUserService;
import com.tarnvik.publicbackend.commuter.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StatisticsController {
  private final StatisticsService statisticsService;
  private final AllowedUserService allowedUserService;

  @GetMapping("/api/admin/statistics")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<StatisticsResponse> getStatistics() {
    return ResponseEntity.ok(new StatisticsResponse(
      statisticsService.getCount(StatName.ROUTES_SHARED),
      statisticsService.getCount(StatName.AI_INTERPRETATION_QUERIES),
      allowedUserService.countUsers()
    ));
  }
}
