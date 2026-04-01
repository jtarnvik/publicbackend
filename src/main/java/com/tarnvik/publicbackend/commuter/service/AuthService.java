package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.MeResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.SettingsResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.mapper.UserSettingsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
  private static final SettingsResponse DEFAULT_SETTINGS =
    new SettingsResponse("9091001000003715", "Skogslöparvägen");

  private final UserSettingsService userSettingsService;
  private final UserSettingsMapper userSettingsMapper;

  public MeResponse buildMeResponse(OAuth2User oauth2User) {
    String email = oauth2User.getAttribute("email");

    String role = oauth2User.getAuthorities().stream()
      .map(GrantedAuthority::getAuthority)
      .filter(a -> a.equals("ROLE_ADMIN"))
      .map(a -> a.substring(5))
      .findFirst()
      .orElse(null);

    SettingsResponse settings = userSettingsService.findByEmail(email)
      .map(userSettingsMapper::toResponse)
      .orElse(DEFAULT_SETTINGS);

    return new MeResponse(
      email,
      oauth2User.getAttribute("name"),
      oauth2User.getAttribute("picture"),
      role,
      settings
    );
  }
}
