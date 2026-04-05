package com.tarnvik.publicbackend.commuter.port.incoming.scheduled;

import com.tarnvik.publicbackend.commuter.model.domain.repository.SharedRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SharedRouteCleanupJob {
  private final SharedRouteRepository sharedRouteRepository;

  @Scheduled(cron = "0 0 0 * * *")
  @Transactional
  public void deleteExpiredRoutes() {
    sharedRouteRepository.deleteByCreateDateBefore(LocalDateTime.now().minusDays(1));
  }
}
