package com.tarnvik.publicbackend.commuter.model.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "shared_route")
@Getter
@Setter
public class SharedRoute {
  @Id
  @Column(name = "id", length = 10, nullable = false)
  private String id;

  @Column(name = "route_data", nullable = false, columnDefinition = "CLOB")
  private String routeData;

  @CreationTimestamp
  @Column(name = "create_date", nullable = false, updatable = false)
  private LocalDateTime createDate;
}
