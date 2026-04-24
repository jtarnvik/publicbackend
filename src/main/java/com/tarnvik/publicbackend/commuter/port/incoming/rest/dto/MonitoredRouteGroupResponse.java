package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

public record MonitoredRouteGroupResponse(String transportMode, int routeGroup, String displayName) {}
