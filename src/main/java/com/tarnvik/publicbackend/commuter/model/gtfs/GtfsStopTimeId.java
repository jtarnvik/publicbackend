package com.tarnvik.publicbackend.commuter.model.gtfs;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
public class GtfsStopTimeId implements Serializable {
  @Column(name = "trip_id", nullable = false, length = 50)
  private String tripId;

  @Column(name = "stop_sequence", nullable = false)
  private int stopSequence;
}
