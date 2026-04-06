package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import com.tarnvik.publicbackend.commuter.model.domain.entity.DeviationInterpretation;
import com.tarnvik.publicbackend.commuter.model.domain.entity.UserHiddenDeviation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserHiddenDeviationRepository extends JpaRepository<UserHiddenDeviation, Long> {
  List<UserHiddenDeviation> findAllByAllowedUserId(Long allowedUserId);

  boolean existsByAllowedUserIdAndDeviationInterpretationId(Long allowedUserId, Long deviationInterpretationId);

  void deleteAllByDeviationInterpretation(DeviationInterpretation deviationInterpretation);

  void deleteAllByAllowedUser(AllowedUser user);
}
