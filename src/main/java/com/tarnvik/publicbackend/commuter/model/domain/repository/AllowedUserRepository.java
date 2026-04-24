package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AllowedUserRepository extends JpaRepository<AllowedUser, Long> {
  Optional<AllowedUser> findByEmail(String email);

  long countByRole(String role);

  @Transactional
  void deleteByEmail(String email);

  @Modifying
  @Transactional
  @Query("UPDATE AllowedUser u SET u.lastLogin = :lastLogin WHERE u.email = :email")
  void updateLastLoginByEmail(@Param("email") String email, @Param("lastLogin") LocalDateTime lastLogin);
}
