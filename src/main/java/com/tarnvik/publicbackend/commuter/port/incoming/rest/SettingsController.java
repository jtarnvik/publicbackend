package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.RecentStopRequest;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.SettingsRequest;
import com.tarnvik.publicbackend.commuter.service.UserSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/protected")
@RequiredArgsConstructor
public class SettingsController {
  private final UserSettingsService userSettingsService;

  @PutMapping("/settings")
  public ResponseEntity<Void> saveSettings(AllowedUser user, @Valid @RequestBody SettingsRequest request) {
    userSettingsService.saveSettings(user, request.stopPointId(), request.stopPointName(), request.useAiInterpretation());
    return ResponseEntity.ok().build();
  }

  @PostMapping("/settings/recent-stops")
  public ResponseEntity<Void> addRecentStop(AllowedUser user, @Valid @RequestBody RecentStopRequest request) {
    userSettingsService.addRecentStop(user, request.stopPointId(), request.stopPointName(), request.stopPointParentName());
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/settings/recent-stops")
  public ResponseEntity<Void> clearRecentStops(AllowedUser user) {
    userSettingsService.clearRecentStops(user);
    return ResponseEntity.ok().build();
  }
}
