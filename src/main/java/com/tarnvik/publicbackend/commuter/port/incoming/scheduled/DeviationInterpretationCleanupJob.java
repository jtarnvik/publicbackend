package com.tarnvik.publicbackend.commuter.port.incoming.scheduled;

import com.tarnvik.publicbackend.commuter.model.domain.entity.DeviationInterpretation;
import com.tarnvik.publicbackend.commuter.model.domain.repository.DeviationInterpretationErrorRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.DeviationInterpretationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviationInterpretationCleanupJob {
  private static final int PURGE_AFTER_DAYS = 28;

  private final DeviationInterpretationRepository deviationInterpretationRepository;
  private final DeviationInterpretationErrorRepository deviationInterpretationErrorRepository;

  @Scheduled(cron = "0 0 0 * * *")
  @Transactional
  public void purgeExpiredInterpretations() {
    LocalDateTime cutoff = LocalDateTime.now().minusDays(PURGE_AFTER_DAYS);
    List<DeviationInterpretation> expired = deviationInterpretationRepository.findAllByCreateDateBefore(cutoff);

    if (expired.isEmpty()) {
      return;
    }

    List<String> expiredHashes = expired.stream().map(DeviationInterpretation::getHash).toList();
    deviationInterpretationErrorRepository.deleteAllByHashIn(expiredHashes);
    deviationInterpretationRepository.deleteAll(expired);

    log.info("Purged {} expired deviation interpretations", expired.size());
  }
}
