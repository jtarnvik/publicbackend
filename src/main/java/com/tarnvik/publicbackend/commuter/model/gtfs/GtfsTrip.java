package com.tarnvik.publicbackend.commuter.model.gtfs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "gtfs_trip")
@Getter
@Setter
public class GtfsTrip {
  @Id
  @Column(name = "trip_id", nullable = false, length = 50)
  private String tripId;

  @Column(name = "route_id", nullable = false, length = 50)
  private String routeId;

  @Column(name = "service_id", nullable = false, length = 50)
  private String serviceId;

  @Column(name = "direction_id", nullable = false)
  private int directionId;
}
