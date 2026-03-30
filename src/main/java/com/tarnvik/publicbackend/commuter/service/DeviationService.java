package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import com.tarnvik.publicbackend.commuter.model.domain.entity.DeviationInterpretation;
import com.tarnvik.publicbackend.commuter.model.domain.entity.UserHiddenDeviation;
import com.tarnvik.publicbackend.commuter.model.domain.repository.AllowedUserRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.DeviationInterpretationRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.UserHiddenDeviationRepository;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.DeviationAction;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.DeviationInterpretationResult;
import com.tarnvik.publicbackend.commuter.model.domain.DeviationResponse;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.claude.ClaudeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviationService {

  private final AllowedUserRepository allowedUserRepository;
  private final DeviationInterpretationRepository deviationInterpretationRepository;
  private final UserHiddenDeviationRepository userHiddenDeviationRepository;
  private final ClaudeProvider claudeProvider;

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
        DeviationInterpretation interpretation = deviationInterpretationRepository.findByHash(hash)
          .orElseGet(() -> interpretAndStore(text, hash));
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

  private DeviationInterpretation interpretAndStore(String text, String hash) {
    DeviationInterpretation entity;
    try {
      DeviationResponse response = claudeProvider.interpretDeviation(text);
      entity = DeviationInterpretation.from(text, hash, response);
    } catch (Exception e) {
      log.warn("AI interpretation failed for hash {}: {}", hash, e.getMessage());
      entity = DeviationInterpretation.withAiError(text, hash);
    }
    return deviationInterpretationRepository.save(entity);
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
