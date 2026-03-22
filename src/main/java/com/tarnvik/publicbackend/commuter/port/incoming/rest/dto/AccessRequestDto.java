package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record AccessRequestDto(@NotBlank String email, String message) {}
