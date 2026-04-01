package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.domain.entity.DeviationInterpretationError;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviationInterpretationErrorRepository extends JpaRepository<DeviationInterpretationError, Long> {
  Optional<DeviationInterpretationError> findByHash(String hash);

  void deleteByHash(String hash);

  void deleteAllByHashIn(List<String> hashes);
}
