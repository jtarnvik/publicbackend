package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsMonitoredRoute;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GtfsMonitoredRouteRepository extends JpaRepository<GtfsMonitoredRoute, Long> {
}
