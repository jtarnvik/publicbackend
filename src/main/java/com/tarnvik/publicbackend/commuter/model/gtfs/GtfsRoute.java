package com.tarnvik.publicbackend.commuter.model.gtfs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "gtfs_route")
@Getter
@Setter
public class GtfsRoute {
  @Id
  @Column(name = "route_id", nullable = false, length = 50)
  private String routeId;

  @Column(name = "route_short_name", nullable = false, length = 20)
  private String routeShortName;

  @Column(name = "route_long_name", length = 200)
  private String routeLongName;

  @Column(name = "route_type", nullable = false)
  private int routeType;
}
