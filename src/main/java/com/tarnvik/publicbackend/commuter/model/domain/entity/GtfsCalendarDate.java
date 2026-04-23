package com.tarnvik.publicbackend.commuter.model.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "gtfs_calendar_date")
@Getter
@Setter
public class GtfsCalendarDate {
  @EmbeddedId
  private GtfsCalendarDateId id;

  @Column(name = "exception_type", nullable = false)
  private int exceptionType;
}
