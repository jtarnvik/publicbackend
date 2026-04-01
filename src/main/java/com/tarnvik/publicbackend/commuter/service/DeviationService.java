package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.DeviationResponse;
import com.tarnvik.publicbackend.commuter.model.domain.dao.DeviationDao;
import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import com.tarnvik.publicbackend.commuter.model.domain.entity.DeviationInterpretation;
import com.tarnvik.publicbackend.commuter.model.domain.entity.DeviationInterpretationError;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.DeviationAction;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.DeviationInterpretationResult;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.claude.ClaudeProvider;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.pushover.PushoverProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviationService {
  private static final int MAX_AI_ERRORS = 5;
  private static final int LOCK_HOURS = 24;
  private static final ExecutorService VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

  private final DeviationDao deviationDao;
  private final ClaudeProvider claudeProvider;
  private final PushoverProvider pushoverProvider;

  private final ConcurrentHashMap<String, CompletableFuture<DeviationInterpretation>> inProgress = new ConcurrentHashMap<>();

  public List<DeviationInterpretationResult> interpretDeviations(List<String> deviationTexts, AllowedUser user) {
    Set<Long> hiddenIds = deviationDao.findHiddenDeviationIds(user.getId());

    List<CompletableFuture<DeviationInterpretationResult>> futures = deviationTexts.stream()
      .map(text -> CompletableFuture.supplyAsync(() -> interpretSingle(text, hiddenIds), VIRTUAL_EXECUTOR))
      .toList();

    return futures.stream()
      .map(CompletableFuture::join)
      .toList();
  }

  public void hideDeviation(Long deviationId, AllowedUser user) {
    DeviationInterpretation interpretation = deviationDao.findInterpretationById(deviationId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    deviationDao.hideDeviationIfNotAlreadyHidden(user, interpretation);
  }

  private DeviationInterpretationResult interpretSingle(String text, Set<Long> hiddenIds) {
    String hash = sha256(text);
    DeviationInterpretation existing = deviationDao.findInterpretationByHash(hash).orElse(null);
    DeviationInterpretation interpretation;
    if (existing != null && !existing.isAiError()) {
      interpretation = existing;
    } else {
      interpretation = resolveWithConcurrencyControl(text, hash, existing);
    }
    return new DeviationInterpretationResult(
      interpretation.getId(),
      interpretation.getImportance(),
      determineAction(interpretation, hiddenIds)
    );
  }

  private DeviationInterpretation resolveWithConcurrencyControl(String text, String hash, DeviationInterpretation existing) {
    CompletableFuture<DeviationInterpretation> myFuture = new CompletableFuture<>();
    CompletableFuture<DeviationInterpretation> existingFuture = inProgress.putIfAbsent(hash, myFuture);

    if (existingFuture != null) {
      try {
        return existingFuture.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for concurrent AI interpretation", e);
      } catch (ExecutionException e) {
        log.warn("Concurrent AI interpretation failed for hash {}, reading result from db", hash);
        return deviationDao.findInterpretationByHash(hash)
          .orElseGet(() -> deviationDao.storeFailedInterpretation(
            DeviationInterpretation.withAiError(text, hash),
            new DeviationInterpretationError(hash)));
      }
    }

    try {
      DeviationInterpretation result = resolveInterpretation(text, hash, existing);
      myFuture.complete(result);
      return result;
    } catch (Exception e) {
      myFuture.completeExceptionally(e);
      throw e;
    } finally {
      inProgress.remove(hash, myFuture);
    }
  }

  private DeviationInterpretation resolveInterpretation(String text, String hash, DeviationInterpretation existing) {
    DeviationInterpretationError errorTracker = deviationDao.findErrorTracker(hash).orElse(null);

    if (errorTracker != null && errorTracker.isLocked()) {
      if (existing != null) {
        return existing;
      }
      return deviationDao.storeFailedInterpretation(DeviationInterpretation.withAiError(text, hash), errorTracker);
    }

    try {
      DeviationResponse response = claudeProvider.interpretDeviation(text);
      if (existing != null) {
        existing.updateFrom(response);
        return deviationDao.storeSuccessfulInterpretation(existing, errorTracker);
      }
      return deviationDao.storeSuccessfulInterpretation(DeviationInterpretation.from(text, hash, response), errorTracker);
    } catch (Exception e) {
      log.warn("AI interpretation failed for hash {}: {}", hash, e.getMessage());
      DeviationInterpretationError tracker = errorTracker != null ? errorTracker : new DeviationInterpretationError(hash);
      recordAiError(tracker, hash);
      DeviationInterpretation errorEntity = existing != null ? existing : DeviationInterpretation.withAiError(text, hash);
      return deviationDao.storeFailedInterpretation(errorEntity, tracker);
    }
  }

  private void recordAiError(DeviationInterpretationError tracker, String hash) {
    tracker.setErrorCount(tracker.getErrorCount() + 1);
    tracker.setLastAttemptAt(LocalDateTime.now());
    if (tracker.getErrorCount() >= MAX_AI_ERRORS) {
      tracker.setLockedUntil(LocalDateTime.now().plusHours(LOCK_HOURS));
      if (tracker.getErrorCount() == MAX_AI_ERRORS) {
        pushoverProvider.sendAiInterpretationErrorNotification(hash.substring(0, 8), tracker.getErrorCount());
      }
    }
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
