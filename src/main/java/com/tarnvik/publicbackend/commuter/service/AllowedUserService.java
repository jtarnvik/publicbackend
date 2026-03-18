package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.repository.AllowedUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AllowedUserService {

  private final AllowedUserRepository allowedUserRepository;

  public boolean isEmailAllowed(String email) {
    return allowedUserRepository.findByEmail(email).isPresent();
  }
}
