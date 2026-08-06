package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

import java.time.Instant;

/**
 * When one vehicle is expected to leave one stop.
 * <p>
 * {@code stopId} is a <em>parent station</em> id, the same namespace as {@link LiveStop#getStopId()}, so a
 * prediction can be matched straight against the drawn chain. The vehicle's own trip counts in platform ids
 * and the conversion has already happened by the time a prediction exists.
 * <p>
 * An {@link Instant} rather than a countdown: the live view polls every eight seconds, and a countdown
 * computed here would be visibly stale between polls. The frontend counts down from this.
 */
public record StopPrediction(String stopId, Instant departure) {}
