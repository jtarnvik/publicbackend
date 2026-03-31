package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.DeviationResponse;
import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import com.tarnvik.publicbackend.commuter.model.domain.entity.DeviationInterpretation;
import com.tarnvik.publicbackend.commuter.model.domain.entity.DeviationInterpretationError;
import com.tarnvik.publicbackend.commuter.model.domain.entity.UserHiddenDeviation;
import com.tarnvik.publicbackend.commuter.model.domain.repository.AllowedUserRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.DeviationInterpretationErrorRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.DeviationInterpretationRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.UserHiddenDeviationRepository;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.DeviationAction;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.DeviationInterpretationResult;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.claude.ClaudeProvider;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.pushover.PushoverProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviationService {

  private static final int MAX_AI_ERRORS = 5;
  private static final int LOCK_HOURS = 24;

  private final AllowedUserRepository allowedUserRepository;
  private final DeviationInterpretationRepository deviationInterpretationRepository;
  private final DeviationInterpretationErrorRepository deviationInterpretationErrorRepository;
  private final UserHiddenDeviationRepository userHiddenDeviationRepository;
  private final ClaudeProvider claudeProvider;
  private final PushoverProvider pushoverProvider;

  @Transactional
  public List<DeviationInterpretationResult> interpretDeviations(List<String> deviationTexts, String email) {
    AllowedUser user = allowedUserRepository.findByEmail(email)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

    Set<Long> hiddenIds = userHiddenDeviationRepository.findAllByAllowedUserId(user.getId()).stream()
      .map(hidden -> hidden.getDeviationInterpretation().getId())
      .collect(Collectors.toSet());

    return deviationTexts.stream()
      .map(text -> {
        String hash = sha256(text);
        DeviationInterpretation existing = deviationInterpretationRepository.findByHash(hash).orElse(null);
        DeviationInterpretation interpretation = (existing != null && !existing.isAiError())
          ? existing
          : resolveInterpretation(text, hash, existing);
        return new DeviationInterpretationResult(
          interpretation.getId(),
          interpretation.getImportance(),
          determineAction(interpretation, hiddenIds)
        );
      })
      .toList();
  }

  @Transactional
  public void hideDeviation(Long deviationId, String email) {
    AllowedUser user = allowedUserRepository.findByEmail(email)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    DeviationInterpretation interpretation = deviationInterpretationRepository.findById(deviationId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    if (!userHiddenDeviationRepository.existsByAllowedUserIdAndDeviationInterpretationId(user.getId(), deviationId)) {
      userHiddenDeviationRepository.save(new UserHiddenDeviation(user, interpretation));
    }
  }

  private DeviationInterpretation resolveInterpretation(String text, String hash, DeviationInterpretation existing) {
    DeviationInterpretationError errorTracker = deviationInterpretationErrorRepository.findByHash(hash).orElse(null);

    if (errorTracker != null && errorTracker.isLocked()) {
      return existing != null ? existing
        : deviationInterpretationRepository.save(DeviationInterpretation.withAiError(text, hash));
    }

    try {
      DeviationResponse response = claudeProvider.interpretDeviation(text);
      if (errorTracker != null) {
        deviationInterpretationErrorRepository.delete(errorTracker);
      }
      if (existing != null) {
        existing.updateFrom(response);
        return deviationInterpretationRepository.save(existing);
      }
      return deviationInterpretationRepository.save(DeviationInterpretation.from(text, hash, response));
    } catch (Exception e) {
      log.warn("AI interpretation failed for hash {}: {}", hash, e.getMessage());
      recordError(hash, errorTracker);
      if (existing != null) {
        return existing;
      }
      return deviationInterpretationRepository.save(DeviationInterpretation.withAiError(text, hash));
    }
  }

  private void recordError(String hash, DeviationInterpretationError existing) {
    DeviationInterpretationError tracker = existing != null ? existing : new DeviationInterpretationError(hash);
    tracker.setErrorCount(tracker.getErrorCount() + 1);
    tracker.setLastAttemptAt(LocalDateTime.now());
    if (tracker.getErrorCount() >= MAX_AI_ERRORS) {
      tracker.setLockedUntil(LocalDateTime.now().plusHours(LOCK_HOURS));
      if (tracker.getErrorCount() == MAX_AI_ERRORS) {
        pushoverProvider.sendAiInterpretationErrorNotification(hash.substring(0, 8), tracker.getErrorCount());
      }
    }
    deviationInterpretationErrorRepository.save(tracker);
  }

  private DeviationAction determineAction(DeviationInterpretation interpretation, Set<Long> hiddenIds) {
    if (hiddenIds.contains(interpretation.getId())) {
      return DeviationAction.HIDDEN_BY_USER;
    }
    if (Boolean.TRUE.equals(interpretation.getAccessibility())) {
      return DeviationAction.HIDDEN_ACCESSIBILITY;
    }
    if (interpretation.isAiError()) {
      return DeviationAction.UNKNOWN;
    }
    return DeviationAction.SHOWN;
  }

  private String sha256(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
