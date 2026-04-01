package com.tarnvik.publicbackend.commuter.model.domain.dao;

import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import com.tarnvik.publicbackend.commuter.model.domain.entity.DeviationInterpretation;
import com.tarnvik.publicbackend.commuter.model.domain.entity.DeviationInterpretationError;
import com.tarnvik.publicbackend.commuter.model.domain.entity.UserHiddenDeviation;
import com.tarnvik.publicbackend.commuter.model.domain.repository.DeviationInterpretationErrorRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.DeviationInterpretationRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.UserHiddenDeviationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DeviationDao {

  private final DeviationInterpretationRepository deviationInterpretationRepository;
  private final DeviationInterpretationErrorRepository deviationInterpretationErrorRepository;
  private final UserHiddenDeviationRepository userHiddenDeviationRepository;

  @Transactional(readOnly = true)
  public Set<Long> findHiddenDeviationIds(Long userId) {
    return userHiddenDeviationRepository.findAllByAllowedUserId(userId).stream()
      .map(h -> h.getDeviationInterpretation().getId())
      .collect(Collectors.toSet());
  }

  @Transactional(readOnly = true)
  public Optional<DeviationInterpretation> findInterpretationByHash(String hash) {
    return deviationInterpretationRepository.findByHash(hash);
  }

  @Transactional(readOnly = true)
  public Optional<DeviationInterpretation> findInterpretationById(Long id) {
    return deviationInterpretationRepository.findById(id);
  }

  @Transactional(readOnly = true)
  public Optional<DeviationInterpretationError> findErrorTracker(String hash) {
    return deviationInterpretationErrorRepository.findByHash(hash);
  }

  @Transactional
  public DeviationInterpretation storeSuccessfulInterpretation(DeviationInterpretation entity, DeviationInterpretationError errorTrackerToDelete) {
    if (errorTrackerToDelete != null) {
      deviationInterpretationErrorRepository.delete(errorTrackerToDelete);
    }
    return deviationInterpretationRepository.save(entity);
  }

  @Transactional
  public DeviationInterpretation storeFailedInterpretation(DeviationInterpretation entity, DeviationInterpretationError tracker) {
    deviationInterpretationErrorRepository.save(tracker);
    if (entity.getId() == null) {
      return deviationInterpretationRepository.save(entity);
    }
    return entity;
  }

  @Transactional
  public void hideDeviationIfNotAlreadyHidden(AllowedUser user, DeviationInterpretation interpretation) {
    if (!userHiddenDeviationRepository.existsByAllowedUserIdAndDeviationInterpretationId(user.getId(), interpretation.getId())) {
      userHiddenDeviationRepository.save(new UserHiddenDeviation(user, interpretation));
    }
  }
}
