package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsVehiclePosition;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When each vehicle was <em>actually</em> at the position it is reporting, as opposed to when the feed
 * last stamped that report.
 * <p>
 * <b>Why this exists.</b> {@code GtfsPredictionUtil} derives delay as
 * {@code timestamp - scheduledTimeAt(position)}, so the error in that number is exactly the gap between
 * when a position was measured and the timestamp attached to it. On the bus feed there is no such gap —
 * positions arrive from live GPS every couple of seconds, {@code speed} is populated, and the reports
 * march steadily along the road. The train feed does not behave that way: a train at line speed will
 * repeat one position for minutes on end and then jump several kilometres at once, and while it repeats,
 * the timestamp sometimes refreshes anyway. Measured over two five-minute samples of the live feed, 19%
 * of consecutive train reports advanced the timestamp without moving the position, against 6% for buses,
 * and the median train position was 29 seconds old against 2 for a bus. Every second of that gap is
 * charged to the traffic and shows up as a train that is minutes later than the platform sign says.
 * <p>
 * The correction is to anchor the observation to the <em>position</em> rather than to the report: the
 * moment a vehicle was at the coordinates it is now sending is the first moment it sent them, so that is
 * the timestamp the delay is measured from. A refreshed stamp against an unchanged position adds no
 * information and is ignored.
 * <p>
 * <b>The cost, which is deliberate.</b> A vehicle that is genuinely standing still — dwelling at a
 * platform, held at a signal — is indistinguishable from a stale report, and its delay is frozen at the
 * moment it stopped rather than growing while it waits. That understates delay for as long as the vehicle
 * is stationary and corrects itself the moment it moves, which is a far smaller error than the one it
 * removes, and it errs towards a countdown that is slightly optimistic rather than one that is minutes
 * pessimistic.
 * <p>
 * The diagnostic behind those numbers is {@code scripts/rt_freshness.py}; re-run it before revisiting any
 * of this, since it all rests on how the upstream feed behaves rather than on anything in this codebase.
 */
@Slf4j
class GtfsPositionAnchors {
  /** A position and the earliest moment the feed claimed the vehicle was at it. */
  private record Anchor(double lat, double lng, Instant observedAt) {}

  private final Map<String, Anchor> byTripId = new ConcurrentHashMap<>();

  /**
   * Folds a fresh feed snapshot in: a vehicle that has moved anchors to its new report, one that has not
   * keeps the timestamp it already had.
   * <p>
   * Anchors for trips absent from the snapshot are dropped, so the map tracks the vehicles currently
   * running rather than every vehicle seen since startup.
   */
  void update(Collection<GtfsVehiclePosition> positions) {
    Set<String> present = new HashSet<>();
    for (GtfsVehiclePosition vp : positions) {
      if (!isAnchorable(vp)) {
        continue;
      }
      present.add(vp.getTripId());
      Anchor reported = new Anchor(vp.getLat(), vp.getLng(), Instant.ofEpochSecond(vp.getTimestamp()));
      byTripId.merge(vp.getTripId(), reported, GtfsPositionAnchors::keepEarlierAtSamePosition);
    }
    byTripId.keySet().retainAll(present);
  }

  /**
   * When the vehicle was at the position it is reporting.
   * <p>
   * Falls back to the report's own timestamp when there is no anchor to consult — a vehicle seen for the
   * first time, or one whose anchor was dropped by a snapshot it was missing from — and to {@code now}
   * when the report carries no usable timestamp at all.
   */
  Instant observedAt(GtfsVehiclePosition vp, Instant now) {
    Anchor anchor = byTripId.get(vp.getTripId());
    if (anchor != null && !hasMoved(anchor, vp)) {
      return anchor.observedAt();
    }
    return vp.getTimestamp() > 0 ? Instant.ofEpochSecond(vp.getTimestamp()) : now;
  }

  /** How many vehicles are currently anchored. For the logs; the map is otherwise private. */
  int size() {
    return byTripId.size();
  }

  private static boolean isAnchorable(GtfsVehiclePosition vp) {
    return vp.getTripId() != null && !vp.getTripId().isBlank() && vp.getTimestamp() > 0;
  }

  /**
   * The merge rule. A vehicle that has moved takes the fresh report wholesale; one that has not keeps its
   * own coordinates and the earlier of the two timestamps — earlier rather than existing, so a report that
   * arrives out of order cannot push an anchor forward in time.
   */
  private static Anchor keepEarlierAtSamePosition(Anchor existing, Anchor reported) {
    if (existing.lat() != reported.lat() || existing.lng() != reported.lng()) {
      return reported;
    }
    return existing.observedAt().isBefore(reported.observedAt()) ? existing : reported;
  }

  /**
   * Whether the feed has sent different coordinates from the ones anchored.
   * <p>
   * Exact comparison is intended: the question is whether the feed re-sent a value it had already sent,
   * not whether two points are geographically close. Live GPS never repeats a reading exactly, so any
   * jitter at all resets the anchor and the vehicle is treated as reporting freshly — which is the safe
   * way round, since it falls back to trusting the feed.
   */
  private static boolean hasMoved(Anchor anchor, GtfsVehiclePosition vp) {
    return anchor.lat() != vp.getLat() || anchor.lng() != vp.getLng();
  }
}
