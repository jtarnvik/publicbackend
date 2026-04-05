package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSharedRouteRequest(@NotBlank String routeData) {}
