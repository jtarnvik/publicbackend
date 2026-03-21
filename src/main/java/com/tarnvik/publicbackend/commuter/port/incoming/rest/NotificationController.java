package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.port.outgoing.rest.pushover.PushoverProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/protected/notification")
public class NotificationController {

  private final PushoverProvider pushoverProvider;

  public NotificationController(PushoverProvider pushoverProvider) {
    this.pushoverProvider = pushoverProvider;
  }

  @PostMapping("/test")
  public ResponseEntity<Void> sendTestNotification() {
    pushoverProvider.sendTestNotification();
    return ResponseEntity.ok().build();
  }
}
