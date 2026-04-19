package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTime;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GtfsStopTimeRepository extends JpaRepository<GtfsStopTime, GtfsStopTimeId> {
}
