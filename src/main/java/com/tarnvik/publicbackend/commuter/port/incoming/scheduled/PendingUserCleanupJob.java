package com.tarnvik.publicbackend.commuter.port.incoming.scheduled;

import com.tarnvik.publicbackend.commuter.model.domain.repository.PendingUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PendingUserCleanupJob {

  private final PendingUserRepository pendingUserRepository;

  @Scheduled(cron = "0 0 0 * * *")
  @Transactional
  public void deleteStaleEntries() {
    pendingUserRepository.deleteByLastLoginAttemptBefore(LocalDateTime.now().minusDays(7));
  }
}
