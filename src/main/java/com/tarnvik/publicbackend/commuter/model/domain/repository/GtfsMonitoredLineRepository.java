package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsMonitoredLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GtfsMonitoredLineRepository extends JpaRepository<GtfsMonitoredLine, Long> {
}
