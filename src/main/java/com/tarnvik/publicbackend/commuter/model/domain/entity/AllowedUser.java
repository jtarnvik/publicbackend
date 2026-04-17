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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "allowed_user")
@Getter
@Setter
public class AllowedUser {
  @TableGenerator(name = "id_generator_allowed_user", table = "id_gen", pkColumnName = "gen_name", valueColumnName = "gen_value",
    pkColumnValue = "allowed_user_gen", initialValue = 10000, allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "id_generator_allowed_user")
  @Column(name = "id")
  @Id
  private Long id;

  @Column(name = "email", nullable = false, unique = true)
  private String email;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "role")
  private String role;

  @CreationTimestamp
  @Column(name = "create_date", updatable = false)
  private LocalDateTime createDate;

  @UpdateTimestamp
  @Column(name = "latest_update")
  private LocalDateTime latestUpdate;
}
