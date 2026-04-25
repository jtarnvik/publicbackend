package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RouteDataResponse {
  String status;
}
