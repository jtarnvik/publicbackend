package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class RouteDataResponse {
  String status;
  LiveTripResponse liveTrip;
  List<LiveVehicleResponse> vehicles;
  /** Present only when the view is focused; `liveTrip.stops` is then a cropped stretch of the full chain. */
  RouteFocusResponse focus;
}
