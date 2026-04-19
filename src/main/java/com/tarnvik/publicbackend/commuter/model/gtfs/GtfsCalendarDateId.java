package com.tarnvik.publicbackend.commuter.model.gtfs;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
public class GtfsCalendarDateId implements Serializable {
  @Column(name = "service_id", nullable = false, length = 50)
  private String serviceId;

  @Column(name = "service_date", nullable = false)
  private LocalDate serviceDate;
}
