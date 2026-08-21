package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

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
  /**
   * When this vehicle is due to leave each chain stop still ahead of it, in the order it reaches them.
   * Stops its own trip does not call at are absent, so a short turn simply stops predicting at its real
   * terminus. Empty when the times could not be worked out.
   */
  List<StopPredictionResponse> stopPredictions;
  /**
   * How late the vehicle is running, in whole seconds, negative when it is early. <b>Null means unknown</b>,
   * which is a different thing from a zero: zero is a measurement saying the vehicle is on time. A consumer
   * that treats the two alike will report a punctual line whenever it is really an unmeasured one.
   */
  Integer delaySeconds;
  /**
   * Where {@code delaySeconds} came from, as a {@code DelaySource} name, and therefore how far to trust it.
   * Null exactly when the delay is.
   */
  String delaySource;
  /**
   * When the vehicle was really at the position reported here, as epoch seconds — the moment the feed first
   * sent these coordinates, which is not always the {@code timestamp} attached to them. The gap between the
   * two is how stale the drawn position is; on the train feed it runs to minutes.
   */
  long observedAt;
  /**
   * Whether the vehicle is standing at one of the stops it is drawn between. A position that has stopped
   * changing is normally one going stale — but not this one, where it is instead as accurate as it ever
   * gets, so anything reading {@code observedAt} as uncertainty must exclude these.
   */
  boolean atStop;
}
