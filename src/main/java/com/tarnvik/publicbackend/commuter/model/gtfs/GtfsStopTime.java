package com.tarnvik.publicbackend.commuter.model.gtfs;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "gtfs_stop_time")
@Getter
@Setter
public class GtfsStopTime {
  @EmbeddedId
  private GtfsStopTimeId id;

  @Column(name = "stop_id", nullable = false, length = 50)
  private String stopId;

  @Column(name = "arrival_time", nullable = false, length = 10)
  private String arrivalTime;

  @Column(name = "departure_time", nullable = false, length = 10)
  private String departureTime;

  @Column(name = "shape_dist_traveled")
  private Double shapeDistTraveled;

  @Column(name = "stop_headsign", length = 100)
  private String stopHeadsign;
}
