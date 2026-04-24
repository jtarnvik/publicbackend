package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import com.tarnvik.publicbackend.commuter.model.domain.repository.AllowedUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AllowedUserService {
  private final AllowedUserRepository allowedUserRepository;

  @Transactional
  public void recordLogin(String email) {
    allowedUserRepository.updateLastLoginByEmail(email, LocalDateTime.now());
  }

  @Transactional(readOnly = true)
  public boolean isEmailAllowed(String email) {
    return allowedUserRepository.findByEmail(email).isPresent();
  }

  @Transactional
  public void createUser(String email, String name) {
    if (allowedUserRepository.findByEmail(email).isEmpty()) {
      var user = new AllowedUser();
      user.setEmail(email);
      user.setName(name);
      allowedUserRepository.save(user);
    }
  }

  @Transactional(readOnly = true)
  public List<AllowedUser> listUsers() {
    return allowedUserRepository.findAll();
  }

  @Transactional(readOnly = true)
  public long countUsers() {
    return allowedUserRepository.count();
  }

  @Transactional
  public void deleteUser(Long id) {
    AllowedUser user = allowedUserRepository.findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if ("ADMIN".equals(user.getRole())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete an administrator");
    }
    allowedUserRepository.delete(user);
  }

  @Transactional
  public void deleteOwnAccount(AllowedUser user) {
    if ("ADMIN".equals(user.getRole()) && allowedUserRepository.countByRole("ADMIN") <= 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete the last administrator account");
    }
    allowedUserRepository.delete(user);
  }
}
