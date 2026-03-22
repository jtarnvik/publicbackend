package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.entity.AccessRequest;
import com.tarnvik.publicbackend.commuter.model.domain.repository.AccessRequestRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.PendingUserRepository;
import com.tarnvik.publicbackend.commuter.port.outgoing.rest.pushover.PushoverProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AccessRequestService {

  private final AccessRequestRepository accessRequestRepository;
  private final PendingUserRepository pendingUserRepository;
  private final PushoverProvider pushoverProvider;

  public AccessRequestService(AccessRequestRepository accessRequestRepository,
                              PendingUserRepository pendingUserRepository,
                              PushoverProvider pushoverProvider) {
    this.accessRequestRepository = accessRequestRepository;
    this.pendingUserRepository = pendingUserRepository;
    this.pushoverProvider = pushoverProvider;
  }

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
}
