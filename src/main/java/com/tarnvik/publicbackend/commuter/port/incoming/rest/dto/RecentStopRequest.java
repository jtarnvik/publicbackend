package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record RecentStopRequest(@NotBlank String stopPointId, @NotBlank String stopPointName, String stopPointParentName) {}
