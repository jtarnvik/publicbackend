package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class MonitoredRouteGroupResponse {
  String transportMode;
  int routeGroup;
  String displayName;
  /**
   * The group's line numbers, so a caller can decide whether a line it is holding belongs to this group.
   * {@code displayName} is the same values joined, but splitting a display string would make its format
   * load-bearing.
   */
  List<String> lines;
  String focusStart;
  String focusEnd;
  boolean onlyFocused;
}
