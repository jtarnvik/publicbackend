package com.tarnvik.publicbackend.commuter.model.gtfs;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * What SL has measured about one scheduled trip, from the GTFS-RT TripUpdates feed.
 * <p>
 * <b>This feed reports backwards.</b> Every stop the vehicle has already passed carries a real,
 * second-precision delay recorded at the platform; every stop still ahead carries {@code delay = 0} and a
 * time on an exact whole minute — the raw timetable, restated. SL publishes observations, not predictions.
 * So this type supplies the delay and nothing else: predicted times are still the timetable shifted by it,
 * and feeding the feed's forward rows through directly would show the plain timetable and be worse than
 * what the geometry already manages.
 * <p>
 * Only rows carrying a delay are kept, in {@code stop_sequence} order. The trip-level {@code delay} field
 * is not kept at all — SL populates it 0% of the time.
 */
public record GtfsTripUpdate(String tripId, List<StopDelay> stopDelays) {
  /** How late the vehicle was at one stop of the trip. Negative when it was early, which buses really are. */
  public record StopDelay(int stopSequence, int delaySeconds) {}

  /**
   * How late the vehicle was the last time SL saw it at a platform at or before {@code stopSequence} — the
   * caller's idea of how far along the trip the vehicle now is.
   * <p>
   * <b>Never the first row, and never a row ahead of the vehicle.</b> The list starts at the trip's origin
   * and keeps every passed stop, so row zero is how late the vehicle left its first station, possibly an
   * hour ago and no longer true. Rows ahead are worse than useless: their zero is the <em>absence</em> of a
   * measurement, and a late vehicle reading one would report itself punctual.
   * <p>
   * That is also why the boundary is a caller-supplied stop sequence rather than a clock comparison. A
   * forward row's time is its timetable time, which for a late vehicle has often already passed, so
   * "everything timed before now" quietly admits exactly the rows that must be excluded — and does so only
   * for the late vehicles this whole exercise is about.
   */
  public Optional<Duration> delayAtOrBefore(int stopSequence) {
    StopDelay latest = null;
    for (StopDelay stopDelay : stopDelays) {
      if (stopDelay.stopSequence() > stopSequence) {
        continue;
      }
      if (latest == null || stopDelay.stopSequence() > latest.stopSequence()) {
        latest = stopDelay;
      }
    }
    return Optional.ofNullable(latest).map(stopDelay -> Duration.ofSeconds(stopDelay.delaySeconds()));
  }
}
