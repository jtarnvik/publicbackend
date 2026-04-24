package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MonitoredRouteGroupResponse {
  String transportMode;
  int routeGroup;
  String displayName;
  String focusStart;
  String focusEnd;
  boolean onlyFocused;
}
