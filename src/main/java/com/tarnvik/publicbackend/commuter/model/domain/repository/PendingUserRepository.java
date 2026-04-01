package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.domain.entity.PendingUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PendingUserRepository extends JpaRepository<PendingUser, Long> {
  Optional<PendingUser> findByEmail(String email);

  @Transactional
  void deleteByEmail(String email);

  @Transactional
  void deleteByLastLoginAttemptBefore(LocalDateTime cutoff);
}
