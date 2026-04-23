package com.tarnvik.publicbackend.commuter.model.domain.entity;

import com.tarnvik.publicbackend.commuter.model.gtfs.GeoPosition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "gtfs_stop")
@Getter
@Setter
public class GtfsStop implements GeoPosition {
  @Id
  @Column(name = "stop_id", nullable = false, length = 50)
  private String stopId;

  @Column(name = "stop_name", nullable = false, length = 100)
  private String stopName;

  @Column(name = "stop_lat", nullable = false)
  private Double stopLat;

  @Column(name = "stop_lon", nullable = false)
  private Double stopLon;

  @Column(name = "location_type")
  private Integer locationType;

  @Column(name = "parent_station", length = 50)
  private String parentStation;

  @Override
  public double getLat() {
    return stopLat;
  }

  @Override
  public double getLng() {
    return stopLon;
  }
}
