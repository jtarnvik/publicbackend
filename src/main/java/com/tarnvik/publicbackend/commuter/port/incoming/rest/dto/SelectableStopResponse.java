package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

/**
 * One stop the user may pick as a favourite. The id is a GTFS parent station id, matching
 * {@code LiveStopResponse.stopId} on the live traffic response — that is what makes the selection line up
 * with the drawn chain.
 */
public record SelectableStopResponse(String stopId, String stopName) {}
