package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import java.util.List;

/**
 * The route group's canonical stop chain — sent once per response, with every vehicle's {@code segIdx}
 * indexing into {@code stops}.
 * <p>
 * The chain is normalized to a single direction: {@code direction} is the direction id it runs in, so a
 * vehicle whose {@code directionId} matches is travelling from {@code stops[0]} towards the end, and one
 * that differs is running the chain backwards.
 * <p>
 * The route and edge variants that {@code LiveTrip} carries (forks, terminus expectations, atypical runs)
 * are deliberately not serialized yet — they will be once the schematic knows how to draw them.
 *
 * @param direction   the direction id the chain is normalized to
 * @param stopHeading the destination the chain heads towards
 * @param stops       the chain, in order
 */
public record LiveTripResponse(int direction, String stopHeading, List<LiveStopResponse> stops) {
}
