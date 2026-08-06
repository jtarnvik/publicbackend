package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

/**
 * When a vehicle is expected to leave one stop of the chain, as epoch seconds.
 * <p>
 * {@code stopId} matches {@link LiveStopResponse#stopId()} on the same response, so the view looks a
 * prediction up by the stop it is drawing rather than by position.
 * <p>
 * An absolute time rather than a countdown, and epoch seconds to match {@code timestamp} on
 * {@link LiveVehicleResponse}: the view polls every eight seconds and counts down locally in between.
 */
public record StopPredictionResponse(String stopId, long departure) {}
