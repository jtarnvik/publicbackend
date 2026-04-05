package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import com.tarnvik.publicbackend.commuter.model.domain.entity.UserSettings;
import com.tarnvik.publicbackend.commuter.model.domain.repository.AllowedUserRepository;
import com.tarnvik.publicbackend.commuter.model.domain.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserSettingsService {
  private final UserSettingsRepository userSettingsRepository;
  private final AllowedUserRepository allowedUserRepository;

  @Transactional(readOnly = true)
  public Optional<UserSettings> findByEmail(String email) {
    return userSettingsRepository.findByAllowedUserEmail(email);
  }

  @Transactional
  public void saveSettings(String email, String stopPointId, String stopPointName, boolean useAiInterpretation) {
    AllowedUser allowedUser = allowedUserRepository.findByEmail(email)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));

    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(email)
      .orElse(new UserSettings());
    settings.setAllowedUser(allowedUser);
    settings.setStopPointId(stopPointId);
    settings.setStopPointName(stopPointName);
    settings.setUseAiInterpretation(useAiInterpretation);
    userSettingsRepository.save(settings);
  }
}
