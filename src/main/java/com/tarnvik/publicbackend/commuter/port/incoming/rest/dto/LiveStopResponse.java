package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

/**
 * One stop in the route group's canonical chain, as a parent station. Order is the list order — a vehicle's
 * {@code segIdx} indexes straight into it.
 *
 * @param stopId            parent station id
 * @param stopName          parent station name
 * @param shapeDistTraveled metres from the start of the chain, for proportional placement
 */
public record LiveStopResponse(String stopId, String stopName, Double shapeDistTraveled) {
}
