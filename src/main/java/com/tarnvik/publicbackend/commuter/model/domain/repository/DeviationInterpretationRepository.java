package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.domain.entity.DeviationInterpretation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviationInterpretationRepository extends JpaRepository<DeviationInterpretation, Long> {

  Optional<DeviationInterpretation> findByHash(String hash);

  boolean existsByHash(String hash);
}
