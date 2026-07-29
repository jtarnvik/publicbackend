package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

import lombok.Builder;
import lombok.Value;

/**
 * How a focused chain terminates at each end, and what is waiting beyond it.
 * <p>
 * Present only when the view is focused; a full chain has nothing truncated and nothing outside it. The
 * truncation flags tell the view where to draw a "line continues" marker, and the counts what to write on it.
 * <p>
 * Only <em>approaching</em> vehicles are counted — those beyond the start heading down into the window, and
 * those beyond the end heading up into it. A vehicle that has already left the window is of no interest to
 * someone deciding whether to set off, so it is dropped rather than counted.
 */
@Value
@Builder
public class RouteFocus {
  /** True when stops before the window were cropped away, so the chain continues above the first stop. */
  boolean truncatedStart;

  /** True when stops after the window were cropped away, so the chain continues below the last stop. */
  boolean truncatedEnd;

  /** Vehicles beyond the start of the window, heading into it. */
  int approachingAtStart;

  /** Vehicles beyond the end of the window, heading into it. */
  int approachingAtEnd;
}
