package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.entity.PendingUser;
import com.tarnvik.publicbackend.commuter.model.domain.repository.PendingUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PendingUserService {

  private final PendingUserRepository pendingUserRepository;

  @Transactional
  public void recordLoginAttempt(String email, String name) {
    PendingUser pendingUser = pendingUserRepository.findByEmail(email)
      .orElseGet(() -> {
        PendingUser newUser = new PendingUser();
        newUser.setEmail(email);
        newUser.setName(name);
        return newUser;
      });

    pendingUser.setLastLoginAttempt(LocalDateTime.now());
    pendingUserRepository.save(pendingUser);
  }
}
