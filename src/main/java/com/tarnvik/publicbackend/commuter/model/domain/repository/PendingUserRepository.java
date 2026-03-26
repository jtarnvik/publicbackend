package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.domain.entity.PendingUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PendingUserRepository extends JpaRepository<PendingUser, Long> {

  Optional<PendingUser> findByEmail(String email);

  void deleteByEmail(String email);

  void deleteByLastLoginAttemptBefore(LocalDateTime cutoff);
}
