package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.MeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  @GetMapping("/me")
  public ResponseEntity<MeResponse> me(@AuthenticationPrincipal OAuth2User user) {
    if (user == null) {
      return ResponseEntity.status(401).build();
    }

    String role = user.getAuthorities().stream()
      .map(GrantedAuthority::getAuthority)
      .filter(a -> a.equals("ROLE_ADMIN"))
      .map(a -> a.substring(5))
      .findFirst()
      .orElse(null);

    return ResponseEntity.ok(new MeResponse(
      user.getAttribute("email"),
      user.getAttribute("name"),
      user.getAttribute("picture"),
      role
    ));
  }

}
