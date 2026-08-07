package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic.ResolveOutcome;
import lombok.Builder;
import lombok.Value;

/**
 * Which live vehicle a departure row refers to, if any.
 * <p>
 * Not finding one is a 200 with {@code outcome} saying so, not an error status. The caller opens the live
 * traffic view either way — the line is worth showing on its own, and a failure to single out one vehicle is
 * an ordinary outcome rather than a fault.
 */
@Value
@Builder
public class ResolveTripResponse {
  /** {@code MATCHED}, {@code NO_MATCH}, {@code AMBIGUOUS} or {@code NO_LIVE_DATA}. */
  ResolveOutcome outcome;

  /** The GTFS trip id to select on the schematic. Null unless {@code outcome} is {@code MATCHED}. */
  String tripId;

  /** The realtime feed's vehicle id. Null unless {@code outcome} is {@code MATCHED}. */
  String vehicleId;
}
