package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * The live picture of one route group: the stop chain, and every vehicle currently running on it.
 * <p>
 * The chain is held once here rather than on each vehicle — every vehicle in {@code vehicles} was located
 * against this exact {@code liveTrip}, and their {@code segIdx} values index into its stops.
 * <p>
 * When the view is focused the chain is a <em>cropped copy</em> of the group's canonical chain and vehicle
 * indices are rebased onto it, so {@code segIdx} always indexes what is actually being sent. Vehicles
 * outside the window are not in the list; {@code focus} says how many are approaching from each end.
 * <p>
 * This is the internal result of
 * {@link com.tarnvik.publicbackend.commuter.service.GtfsRealtimeService#getRouteData}. It carries the full
 * domain objects; the REST layer maps it down to
 * {@link com.tarnvik.publicbackend.commuter.port.incoming.rest.dto.RouteDataResponse} and sends only what
 * the view needs.
 */
@Value
@Builder
public class RouteData {
  /** {@code "OK"}, or a short description of why the data could not be produced. */
  String status;

  /** The stop chain, cropped when focused. Null when the data could not be produced. */
  LiveTrip liveTrip;

  /** The located vehicles, empty when none are running or no static data is loaded. */
  List<LiveVehicle> vehicles;

  /** How the chain terminates and what is approaching beyond it. Null when the view is not focused. */
  RouteFocus focus;
}
