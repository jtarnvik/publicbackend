package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsTrip;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GtfsTripRepository extends JpaRepository<GtfsTrip, String> {
}
