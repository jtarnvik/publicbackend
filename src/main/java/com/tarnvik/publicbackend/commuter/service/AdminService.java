package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.entity.AccessRequest;
import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import com.tarnvik.publicbackend.commuter.model.domain.repository.AccessRequestRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.AllowedUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

  private final AccessRequestRepository accessRequestRepository;
  private final AllowedUserRepository allowedUserRepository;

  public List<AccessRequest> listAccessRequests() {
    return accessRequestRepository.findAll();
  }

  public void approveAccessRequest(Long id) {
    AccessRequest request = accessRequestRepository.findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    if (allowedUserRepository.findByEmail(request.getEmail()).isEmpty()) {
      var allowedUser = new AllowedUser();
      allowedUser.setEmail(request.getEmail());
      allowedUser.setName(request.getName());
      allowedUserRepository.save(allowedUser);
    }

    accessRequestRepository.delete(request);
  }

  public void rejectAccessRequest(Long id) {
    AccessRequest request = accessRequestRepository.findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    accessRequestRepository.delete(request);
  }

  public List<AllowedUser> listUsers() {
    return allowedUserRepository.findAll();
  }

  public void deleteUser(Long id) {
    AllowedUser user = allowedUserRepository.findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if ("ADMIN".equals(user.getRole())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete an administrator");
    }
    allowedUserRepository.delete(user);
  }
}
