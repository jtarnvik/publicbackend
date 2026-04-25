package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface GtfsDownloadLogRepository extends JpaRepository<GtfsDownloadLog, Long> {
  Optional<GtfsDownloadLog> findByDate(LocalDate date);

  Optional<GtfsDownloadLog> findTopByOrderByDateDesc();

  void deleteByDateBefore(LocalDate date);
}
