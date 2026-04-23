package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsCalendarDate;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsCalendarDateId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GtfsCalendarDateRepository extends JpaRepository<GtfsCalendarDate, GtfsCalendarDateId> {
}
