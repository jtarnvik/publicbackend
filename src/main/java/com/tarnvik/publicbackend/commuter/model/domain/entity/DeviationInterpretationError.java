package com.tarnvik.publicbackend.commuter.model.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "deviation_interpretation_errors")
@Getter
@Setter
@NoArgsConstructor
public class DeviationInterpretationError {
  @TableGenerator(name = "id_generator_deviation_interpretation_errors", table = "id_gen", pkColumnName = "gen_name", valueColumnName = "gen_value",
    pkColumnValue = "deviation_interpretation_errors_gen", initialValue = 10000, allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "id_generator_deviation_interpretation_errors")
  @Column(name = "id")
  @Id
  private Long id;

  @Column(name = "hash", nullable = false, unique = true, length = 64)
  private String hash;

  @Column(name = "error_count", nullable = false)
  private int errorCount;

  @Column(name = "last_attempt_at", nullable = false)
  private LocalDateTime lastAttemptAt;

  @Column(name = "locked_until")
  private LocalDateTime lockedUntil;

  @CreationTimestamp
  @Column(name = "create_date", updatable = false)
  private LocalDateTime createDate;

  @UpdateTimestamp
  @Column(name = "latest_update")
  private LocalDateTime latestUpdate;

  public DeviationInterpretationError(String hash) {
    this.hash = hash;
    this.errorCount = 0;
  }

  public boolean isLocked() {
    return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
  }
}
