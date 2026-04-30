package com.tarnvik.publicbackend.commuter.model.domain.entity;

import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.GroupKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "gtfs_monitored_route")
@Getter
@Setter
public class GtfsMonitoredRoute {
  @TableGenerator(name = "id_generator_gtfs_monitored_route", table = "id_gen", pkColumnName = "gen_name",
    valueColumnName = "gen_value", pkColumnValue = "gtfs_monitored_route_gen", initialValue = 10000, allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "id_generator_gtfs_monitored_route")
  @Column(name = "id")
  @Id
  private Long id;

  @Column(name = "route_short_name", nullable = false, length = 20)
  private String routeShortName;

  @Enumerated(EnumType.STRING)
  @Column(name = "transport_mode", nullable = false, length = 20)
  private TransportMode transportMode;

  @Column(name = "route_group", nullable = false)
  private int routeGroup;

  @Column(name = "focus_start", length = 50)
  private String focusStart;

  @Column(name = "focus_end", length = 50)
  private String focusEnd;

  @Column(name = "only_focused", nullable = false)
  private boolean onlyFocused;

  @CreationTimestamp
  @Column(name = "create_date", updatable = false)
  private LocalDateTime createDate;

  @UpdateTimestamp
  @Column(name = "latest_update")
  private LocalDateTime latestUpdate;

  public GroupKey getGroupKey() {
    return new GroupKey(transportMode, routeGroup);
  }
}
