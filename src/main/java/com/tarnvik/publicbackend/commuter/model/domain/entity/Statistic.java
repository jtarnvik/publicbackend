package com.tarnvik.publicbackend.commuter.model.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "statistics")
@Getter
@Setter
public class Statistic {
  @TableGenerator(name = "id_generator_statistics", table = "id_gen", pkColumnName = "gen_name", valueColumnName = "gen_value",
    pkColumnValue = "statistics_gen", initialValue = 10000, allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "id_generator_statistics")
  @Column(name = "id")
  @Id
  private Long id;

  @Column(name = "name", nullable = false, unique = true)
  private String name;

  @Column(name = "counter", nullable = false)
  private long counter;

  @CreationTimestamp
  @Column(name = "create_date", nullable = false, updatable = false)
  private LocalDateTime createDate;
}
