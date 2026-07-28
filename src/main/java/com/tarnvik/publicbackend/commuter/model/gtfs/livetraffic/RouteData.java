package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

import java.util.List;

/**
 * The live picture of one route group: the canonical stop chain, and every vehicle currently running on it.
 * <p>
 * The chain is held once here rather than on each vehicle — every vehicle in {@code vehicles} was located
 * against this exact {@code liveTrip}, and their {@code segIdx} values index into its stops.
 * <p>
 * This is the internal result of
 * {@link com.tarnvik.publicbackend.commuter.service.GtfsRealtimeService#getRouteData}. It carries the full
 * domain objects; the REST layer maps it down to
 * {@link com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.RouteDataResponse} and sends only what
 * the view needs.
 *
 * @param status   {@code "OK"}, or a short description of why the data could not be produced
 * @param liveTrip the group's stop chain, null when the data could not be produced
 * @param vehicles the located vehicles, empty when none are running or no static data is loaded
 */
public record RouteData(String status, LiveTrip liveTrip, List<LiveVehicle> vehicles) {
}
