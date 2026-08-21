package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

/**
 * Where a vehicle's delay figure came from.
 * <p>
 * <b>Why a delay needs a provenance at all.</b> The number alone cannot say whether a zero means "measured,
 * and the vehicle is on time" or "we could not tell, so nothing has been applied". A consumer that averages
 * the two together describes a punctual network that does not exist, and it does so confidently. The delay
 * is therefore nullable and this tag rides alongside it: a delay is either absent, or present with a source
 * that says how much to trust it.
 * <p>
 * Two further sources have been considered and are not implemented. SL's own departures API carries a
 * {@code scheduled}/{@code expected} pair and remains the natural fallback if {@link #TRIP_UPDATES} is ever
 * withdrawn; and the geometry could be improved to measure at the last stop the vehicle passed rather than
 * interpolating mid-segment. Add a constant when one of them starts producing values, not before.
 */
public enum DelaySource {
  /**
   * SL's own measurement at the last platform the vehicle passed, from the GTFS-RT TripUpdates feed. The
   * best available: a recorded arrival rather than anything inferred here, so it carries neither the
   * projection's mid-segment bias nor its distance guard.
   */
  TRIP_UPDATES,

  /**
   * Interpolated from where the vehicle projects onto its own trip — the scheduled time at that point
   * against the moment it was observed there. Serviceable, but it reads a straight line between two stops
   * as if it were the track, so it runs pessimistic just after a departure and corrects into the next stop.
   * Treat a value from here as good to a minute or so rather than to the second.
   */
  GEOMETRY
}
