package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStop;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GtfsStopRepository extends JpaRepository<GtfsStop, String> {
}
