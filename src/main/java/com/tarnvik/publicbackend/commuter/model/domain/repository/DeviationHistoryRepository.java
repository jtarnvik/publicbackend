package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.domain.entity.DeviationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviationHistoryRepository extends JpaRepository<DeviationHistory, Long> {
}
