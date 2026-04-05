package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.domain.entity.SharedRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface SharedRouteRepository extends JpaRepository<SharedRoute, String> {
  void deleteByCreateDateBefore(LocalDateTime cutoff);
}
