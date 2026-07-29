package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import lombok.Builder;
import lombok.Value;

/**
 * How a focused chain terminates, and what is waiting beyond it. Absent when the view is not focused.
 * <p>
 * A truncated end means the line continues past the stops that were sent, so the view draws a
 * "continues here" marker rather than an end of line. The counts are of <em>approaching</em> vehicles only —
 * those heading into the window — so a count of zero means nothing is on its way in, not that nothing exists
 * out there.
 */
@Value
@Builder
public class RouteFocusResponse {
  boolean truncatedStart;
  boolean truncatedEnd;
  int approachingAtStart;
  int approachingAtEnd;
}
