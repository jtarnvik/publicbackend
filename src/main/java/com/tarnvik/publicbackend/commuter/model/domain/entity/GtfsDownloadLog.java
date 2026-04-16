package com.tarnvik.publicbackend.commuter.model.domain.entity;

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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "gtfs_download_log")
@Getter
@Setter
public class GtfsDownloadLog {
  @TableGenerator(name = "id_generator_gtfs_download_log", table = "id_gen", pkColumnName = "gen_name",
    valueColumnName = "gen_value", pkColumnValue = "gtfs_download_log_gen", initialValue = 10000, allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "id_generator_gtfs_download_log")
  @Column(name = "id")
  @Id
  private Long id;

  @Column(name = "date", nullable = false, unique = true)
  private LocalDate date;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private GtfsDownloadStatus status;

  @Column(name = "error_message", length = 2000)
  private String errorMessage;

  @Column(name = "download_start_time")
  private LocalDateTime downloadStartTime;

  @Column(name = "unzip_start_time")
  private LocalDateTime unzipStartTime;

  @Column(name = "parse_start_time")
  private LocalDateTime parseStartTime;

  @Column(name = "end_time")
  private LocalDateTime endTime;

  @CreationTimestamp
  @Column(name = "create_date")
  private LocalDateTime createDate;

  @UpdateTimestamp
  @Column(name = "latest_update")
  private LocalDateTime latestUpdate;
}
