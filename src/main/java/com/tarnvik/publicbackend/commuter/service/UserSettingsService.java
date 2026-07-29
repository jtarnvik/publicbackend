package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.FavouriteStop;
import com.tarnvik.publicbackend.commuter.model.domain.RecentStop;
import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import com.tarnvik.publicbackend.commuter.model.domain.entity.UserSettings;
import com.tarnvik.publicbackend.commuter.model.domain.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserSettingsService {
  private static final int MAX_RECENT_STOPS = 5;
  private static final int MAX_FAVOURITE_STOPS = 10;

  private final UserSettingsRepository userSettingsRepository;

  @Transactional(readOnly = true)
  public Optional<UserSettings> findByEmail(String email) {
    return userSettingsRepository.findByAllowedUserEmail(email);
  }

  /**
   * Saves everything the settings dialog owns in one transaction.
   * <p>
   * {@code favouriteStops} null means leave the stored list alone; an empty list clears it. Deliberately
   * does not touch {@code recentStops}, which are written on a different trigger entirely.
   */
  @Transactional
  public void saveSettings(AllowedUser user, String stopPointId, String stopPointName,
                           boolean useAiInterpretation, List<FavouriteStop> favouriteStops) {
    UserSettings settings = userSettingsRepository.findByAllowedUserEmail(user.getEmail())
      .orElse(new UserSettings());
    settings.setAllowedUser(user);
    settings.setStopPointId(stopPointId);
    settings.setStopPointName(stopPointName);
    settings.setUseAiInterpretation(useAiInterpretation);
    if (favouriteStops != null) {
      settings.setFavouriteStops(sanitizeFavourites(favouriteStops));
    }
    userSettingsRepository.save(settings);
  }

  /**
   * Drops blanks, removes duplicate stop ids and enforces the cap — silently, never by failing the request.
   * A stop legitimately appears in more than one route group (Alvik is on the green line and is the
   * terminus of bus 112), so ticking it in either place must not consume two of the ten.
   * <p>
   * Order is the order the user picked in, and nothing consumes it — lookup on the schematic is by id. Do
   * not "fix" this by sorting.
   * <p>
   * Ids are deliberately not validated against the GTFS dataset: it is empty outside the {@code local}
   * profile, so validating would reject every favourite in production.
   */
  private List<FavouriteStop> sanitizeFavourites(List<FavouriteStop> favouriteStops) {
    List<FavouriteStop> sanitized = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (FavouriteStop stop : favouriteStops) {
      if (stop == null || stop.stopId() == null || stop.stopId().isBlank()) {
        continue;
      }
      if (!seen.add(stop.stopId())) {
        continue;
      }
      sanitized.add(stop);
      if (sanitized.size() == MAX_FAVOURITE_STOPS) {
        break;
      }
    }
    return sanitized;
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
