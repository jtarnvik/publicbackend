package com.tarnvik.publicbackend.commuter.port.incoming.rest;

import com.tarnvik.publicbackend.commuter.model.domain.FavouriteStop;
import com.tarnvik.publicbackend.commuter.model.domain.entity.AllowedUser;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.FavouriteStopRequest;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.FavouriteStopsRequest;
import com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.LiveTrafficViewRequest;
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

import java.util.List;

@RestController
@RequestMapping("/api/protected")
@RequiredArgsConstructor
public class SettingsController {
  private final UserSettingsService userSettingsService;

  @PutMapping("/settings")
  public ResponseEntity<Void> saveSettings(AllowedUser user, @Valid @RequestBody SettingsRequest request) {
    userSettingsService.saveSettings(user, request.stopPointId(), request.stopPointName(),
      request.useAiInterpretation(), toFavouriteStops(request.favouriteStops()));
    return ResponseEntity.ok().build();
  }

  /** Null survives as null — the service reads it as "leave the stored favourites alone". */
  private List<FavouriteStop> toFavouriteStops(List<FavouriteStopRequest> requested) {
    if (requested == null) {
      return null;
    }
    return requested.stream()
      .map(stop -> new FavouriteStop(stop.stopId(), stop.stopName()))
      .toList();
  }

  /**
   * The favourites on their own, saved when a stop is tapped on the live traffic schematic. Separate from
   * {@code PUT /settings} because the live traffic view does not own the stop point or the AI flag, and
   * echoing them back on every tap would overwrite a change made in the settings dialog elsewhere.
   */
  @PutMapping("/settings/favourite-stops")
  public ResponseEntity<Void> saveFavouriteStops(AllowedUser user, @Valid @RequestBody FavouriteStopsRequest request) {
    userSettingsService.saveFavouriteStops(user, toFavouriteStops(request.favouriteStops()));
    return ResponseEntity.ok().build();
  }

  @PutMapping("/settings/live-traffic-view")
  public ResponseEntity<Void> saveLiveTrafficView(AllowedUser user, @Valid @RequestBody LiveTrafficViewRequest request) {
    userSettingsService.saveLiveTrafficView(user, request.transportMode(), request.routeGroup(), request.focused());
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
