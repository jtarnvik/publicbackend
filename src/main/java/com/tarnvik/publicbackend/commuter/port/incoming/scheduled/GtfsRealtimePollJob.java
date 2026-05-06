package com.tarnvik.publicbackend.commuter.port.incoming.scheduled;

import com.tarnvik.publicbackend.commuter.port.outgoing.rest.samtrafiken.SamtrafikenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class GtfsRealtimePollJob {
  private final SamtrafikenProvider samtrafikenProvider;

  // TODO: Remove this job once live vehicle positions are consumed by C-block schematic view
  @Scheduled(cron = "0 */5 6-23 * * *", zone = "Europe/Stockholm")
  public void pollVehiclePositions() {
    try {
      samtrafikenProvider.fetchVehiclePositions();
    } catch (Exception e) {
      log.warn("GTFS-RT vehicle position poll failed: {}", e.getMessage());
    }
  }
}
