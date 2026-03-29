package com.tarnvik.publicbackend.commuter.model.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_hidden_deviations",
  uniqueConstraints = @UniqueConstraint(columnNames = {"allowed_user_id", "deviation_interpretation_id"}))
@Getter
@Setter
public class UserHiddenDeviation {

  @TableGenerator(name = "id_generator_user_hidden_deviations", table = "id_gen", pkColumnName = "gen_name", valueColumnName = "gen_value",
    pkColumnValue = "user_hidden_deviations_gen", initialValue = 10000, allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "id_generator_user_hidden_deviations")
  @Column(name = "id")
  @Id
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "allowed_user_id", nullable = false)
  private AllowedUser allowedUser;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "deviation_interpretation_id", nullable = false)
  private DeviationInterpretation deviationInterpretation;

  @CreationTimestamp
  @Column(name = "create_date")
  private LocalDateTime createDate;

  @UpdateTimestamp
  @Column(name = "latest_update")
  private LocalDateTime latestUpdate;
}
