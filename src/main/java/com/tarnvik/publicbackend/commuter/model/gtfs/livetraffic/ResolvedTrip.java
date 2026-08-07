package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

/**
 * The answer to a {@link TripQuery}: which live vehicle is the one that departure row refers to.
 *
 * @param outcome   how the attempt ended; only {@link ResolveOutcome#MATCHED} carries the two ids
 * @param tripId    the GTFS trip the vehicle is running, which is what the schematic selects on
 * @param vehicleId the realtime feed's own vehicle id — unused by the view today, kept because tracking a
 *                  specific bus will need to survive its trip ending
 */
public record ResolvedTrip(ResolveOutcome outcome, String tripId, String vehicleId) {
  public static ResolvedTrip of(ResolveOutcome outcome) {
    return new ResolvedTrip(outcome, null, null);
  }

  public static ResolvedTrip matched(String tripId, String vehicleId) {
    return new ResolvedTrip(ResolveOutcome.MATCHED, tripId, vehicleId);
  }
}
