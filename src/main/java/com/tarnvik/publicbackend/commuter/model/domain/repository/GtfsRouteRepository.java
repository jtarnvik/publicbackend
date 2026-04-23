package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsRoute;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GtfsRouteRepository extends JpaRepository<GtfsRoute, String> {
}
