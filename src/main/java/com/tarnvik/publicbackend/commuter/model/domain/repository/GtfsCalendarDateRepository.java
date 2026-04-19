package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsCalendarDate;
import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsCalendarDateId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GtfsCalendarDateRepository extends JpaRepository<GtfsCalendarDate, GtfsCalendarDateId> {
}
