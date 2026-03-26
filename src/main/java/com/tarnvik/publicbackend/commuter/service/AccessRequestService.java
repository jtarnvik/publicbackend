package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.entity.AccessRequest;
import com.tarnvik.publicbackend.commuter.model.domain.repository.AccessRequestRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.PendingUserRepository;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.pushover.PushoverProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessRequestService {

  private final AccessRequestRepository accessRequestRepository;
  private final PendingUserRepository pendingUserRepository;
  private final PushoverProvider pushoverProvider;
  private final AllowedUserService allowedUserService;

  public void handleAccessRequest(String email, String message) {
    var pendingUser = pendingUserRepository.findByEmail(email);
    if (pendingUser.isEmpty()) {
      log.info("Access request dropped - email not in pending_user: {}", email);
      return;
    }

    if (accessRequestRepository.findByEmail(email).isPresent()) {
      log.info("Access request dropped - already requested: {}", email);
      return;
    }

    var request = new AccessRequest();
    request.setEmail(email);
    request.setName(pendingUser.get().getName());
    request.setMessage(message);
    accessRequestRepository.save(request);

    pushoverProvider.sendAccessRequestNotification(pendingUser.get().getName(), email, message);
  }

  public List<AccessRequest> listAccessRequests() {
    return accessRequestRepository.findAll();
  }

  public long countAccessRequests() {
    return accessRequestRepository.count();
  }

  public void approveAccessRequest(Long id) {
    AccessRequest request = accessRequestRepository.findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    allowedUserService.createUser(request.getEmail(), request.getName());
    accessRequestRepository.delete(request);
  }

  public void rejectAccessRequest(Long id) {
    AccessRequest request = accessRequestRepository.findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    accessRequestRepository.delete(request);
  }
}
