package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.entity.UserSettings;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.LiveTrafficViewResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.MeResponse;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.SettingsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {
  private static final SettingsResponse DEFAULT_SETTINGS = SettingsResponse.builder()
    .stopPointId("9091001000003715")
    .stopPointName("Skogslöparvägen")
    .useAiInterpretation(true)
    .recentStops(List.of())
    .favouriteStops(List.of())
    .build();

  private final UserSettingsService userSettingsService;
  private final AllowedUserService allowedUserService;

  public MeResponse buildMeResponse(OAuth2User oauth2User) {
    String email = oauth2User.getAttribute("email");
    allowedUserService.recordLogin(email);

    String role = oauth2User.getAuthorities().stream()
      .map(GrantedAuthority::getAuthority)
      .filter(a -> a.equals("ROLE_ADMIN"))
      .map(a -> a.substring(5))
      .findFirst()
      .orElse(null);

    SettingsResponse settings = userSettingsService.findByEmail(email)
      .map(s -> SettingsResponse.builder()
        .stopPointId(s.getStopPointId() != null ? s.getStopPointId() : DEFAULT_SETTINGS.getStopPointId())
        .stopPointName(s.getStopPointName() != null ? s.getStopPointName() : DEFAULT_SETTINGS.getStopPointName())
        .useAiInterpretation(s.isUseAiInterpretation())
        .recentStops(s.getRecentStops())
        .favouriteStops(s.getFavouriteStops())
        .liveTrafficView(toLiveTrafficView(s))
        .build())
      .orElse(DEFAULT_SETTINGS);

    return new MeResponse(
      email,
      oauth2User.getAttribute("name"),
      oauth2User.getAttribute("picture"),
      role,
      settings
    );
  }

  /**
   * Null unless a group was actually stored. The focus flag rides along as-is, including its own null,
   * which the view reads as "use this group's default" rather than as "unfocused".
   */
  private static LiveTrafficViewResponse toLiveTrafficView(UserSettings settings) {
    if (settings.getLiveTrafficTransportMode() == null || settings.getLiveTrafficRouteGroup() == null) {
      return null;
    }
    return new LiveTrafficViewResponse(
      settings.getLiveTrafficTransportMode(),
      settings.getLiveTrafficRouteGroup(),
      settings.getLiveTrafficFocused()
    );
  }
}
