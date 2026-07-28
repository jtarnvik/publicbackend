package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import lombok.Builder;
import lombok.Value;

/**
 * One vehicle as the live traffic view sees it.
 * <p>
 * {@code segIdx} and {@code segmentFraction} place the vehicle on the chain in {@code liveTrip} on the
 * enclosing response: it is between {@code stops[segIdx]} and {@code stops[segIdx + 1]},
 * {@code segmentFraction} of the way along that segment. Compare {@code directionId} against the chain's
 * {@code direction} to know which way it is moving through the list.
 * <p>
 * {@code distanceMetres} is how far the vehicle actually is from that segment — a large value means the
 * geometric match is poor and the placement should be treated with suspicion.
 */
@Value
@Builder
public class LiveVehicleResponse {
  String vehicleId;
  String tripId;
  /** Where this vehicle terminates — its own trip's destination, not the end of the chain. */
  String destination;
  double lat;
  double lng;
  float bearing;
  long timestamp;
  int directionId;
  int segIdx;
  double segmentFraction;
  double distanceMetres;
}
