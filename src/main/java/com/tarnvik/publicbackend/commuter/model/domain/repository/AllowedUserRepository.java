package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface AllowedUserRepository extends JpaRepository<AllowedUser, Long> {

  Optional<AllowedUser> findByEmail(String email);

  @Transactional
  void deleteByEmail(String email);
}
