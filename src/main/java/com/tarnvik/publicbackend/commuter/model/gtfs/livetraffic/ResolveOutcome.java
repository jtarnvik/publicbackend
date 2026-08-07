package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

/**
 * How an attempt to resolve a departure row to a live vehicle ended.
 * <p>
 * Only {@link #MATCHED} carries a trip id. The rest are all reported to the user as a note beside a view
 * that still works — the line is shown either way, and the vehicle highlight is the bonus that was missed.
 */
public enum ResolveOutcome {
  /** A single live vehicle matched the departure. */
  MATCHED,

  /**
   * Nothing matched. Ordinary rather than exceptional: SL shows a journey as running slightly before the
   * vehicle appears in the realtime feed, and a vehicle that has stopped reporting stays on SL's board.
   */
  NO_MATCH,

  /**
   * More than one live vehicle matched, so no single one can be pointed at. Should not happen — the key
   * includes the destination, which separates the only collision the timetable actually contains — so this
   * is a guard rather than an expected path.
   */
  AMBIGUOUS,

  /** No static dataset, or no realtime data to search. Nothing to do with this particular departure. */
  NO_LIVE_DATA
}
