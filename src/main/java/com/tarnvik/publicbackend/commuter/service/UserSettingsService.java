package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.RecentStop;
import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import com.tarnvik.publicbackend.commuter.model.domain.entity.UserSettings;
import com.tarnvik.publicbackend.commuter.model.domain.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserSettingsService {
  private static final int MAX_RECENT_STOPS = 5;

  private final UserSettingsRepository userSettingsRepository;

  @Transactional(readOnly = true)
  public Optional<UserSettings> findByEmail(String email) {
    return userSettingsRepository.findByAllowedUserEmail(email);
  }

  @Transactional
  public void saveSettings(AllowedUser user, String stopPointId, String stopPointName, boolean useAiInterpretation) {
    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(user.getEmail())
      .orElse(new UserSettings());
    settings.setAllowedUser(user);
    settings.setStopPointId(stopPointId);
    settings.setStopPointName(stopPointName);
    settings.setUseAiInterpretation(useAiInterpretation);
    userSettingsRepository.save(settings);
  }

  @Transactional
  public void addRecentStop(AllowedUser user, String stopPointId, String stopPointName, String stopPointParentName) {
    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(user.getEmail())
      .orElseGet(UserSettings::new);
    settings.setAllowedUser(user);

    List<RecentStop> recent = new ArrayList<>(settings.getRecentStops());
    recent.removeIf(s -> s.stopPointId().equals(stopPointId));
    recent.addFirst(new RecentStop(stopPointId, stopPointName, stopPointParentName));
    if (recent.size() > MAX_RECENT_STOPS) {
      recent = recent.subList(0, MAX_RECENT_STOPS);
    }
    settings.setRecentStops(recent);
    userSettingsRepository.save(settings);
  }

  @Transactional
  public void clearRecentStops(AllowedUser user) {
    userSettingsRepository.findByAllowedUserEmail(user.getEmail()).ifPresent(settings -> {
      settings.setRecentStops(List.of());
      userSettingsRepository.save(settings);
    });
  }
}
