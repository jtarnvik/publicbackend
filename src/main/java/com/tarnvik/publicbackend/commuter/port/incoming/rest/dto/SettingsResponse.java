package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import com.tarnvik.publicbackend.commuter.model.domain.FavouriteStop;
import com.tarnvik.publicbackend.commuter.model.domain.RecentStop;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * A user's settings as sent to the frontend, nested inside {@link MeResponse}.
 * <p>
 * A builder rather than a record: at five fields the positional constructor was becoming unreadable, and
 * the one construction site already passes two ternaries. The JSON is unchanged by the switch — Lombok's
 * {@code isUseAiInterpretation()} serializes under the same name the record accessor did.
 */
@Value
@Builder
public class SettingsResponse {
  String stopPointId;
  String stopPointName;
  boolean useAiInterpretation;
  List<RecentStop> recentStops;
  List<FavouriteStop> favouriteStops;
  /** Null until the user has opened the live traffic view and changed something. */
  LiveTrafficViewResponse liveTrafficView;
}
