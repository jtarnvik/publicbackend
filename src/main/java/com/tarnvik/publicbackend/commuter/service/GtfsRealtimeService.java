package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsDataset;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsVehiclePosition;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.samtrafiken.SamtrafikenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GtfsRealtimeService {
  private final SamtrafikenProvider samtrafikenProvider;
  private final GtfsAccessService gtfsAccessService; // will be used when POC expands

  public GtfsRealtimeService(SamtrafikenProvider samtrafikenProvider, GtfsAccessService gtfsAccessService) {
    this.samtrafikenProvider = samtrafikenProvider;
    this.gtfsAccessService = gtfsAccessService;
  }

  public void poc() {
    try {
      GtfsDataset dataset = gtfsAccessService.getDataset();
      List<GtfsVehiclePosition> gtfsVehiclePositions = samtrafikenProvider.fetchVehiclePositions();
      log.info("Total number of vehicles {}", gtfsVehiclePositions.size());

      Map<String, Long> countByStatus = gtfsVehiclePositions.stream()
        .collect(Collectors.groupingBy(vp -> vp.getCurrentStatus().name(), Collectors.counting()));
      
      countByStatus.forEach((status, count) -> log.info("  {} -> {}", status, count));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
