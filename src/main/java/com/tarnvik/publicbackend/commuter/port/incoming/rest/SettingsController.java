package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.SettingsRequest;
import com.tarnvik.publicbackend.commuter.service.UserSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
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
  public ResponseEntity<Void> saveSettings(
    @AuthenticationPrincipal OAuth2User user,
    @Valid @RequestBody SettingsRequest request
  ) {
    userSettingsService.saveSettings(user.getAttribute("email"), request.stopPointId(), request.stopPointName(), request.useAiInterpretation());
    return ResponseEntity.ok().build();
  }
}
