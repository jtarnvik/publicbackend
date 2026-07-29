package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * The stops of one route group, for the favourite stop picker in the settings dialog.
 * <p>
 * Kept out of {@code /route-groups}, which the live traffic view fetches on every mount and which would
 * grow roughly thirtyfold for data that view never uses.
 * <p>
 * A stop can appear in more than one group — Alvik is on the green line and is the terminus of bus 112 —
 * and is deliberately not deduplicated here: seeing it under both lines is how a user looks for it. The
 * selection is by stop id, so ticking either occurrence selects the same stop.
 */
@Value
@Builder
public class RouteGroupStopsResponse {
  String transportMode;
  int routeGroup;
  String displayName;
  List<SelectableStopResponse> stops;
}
