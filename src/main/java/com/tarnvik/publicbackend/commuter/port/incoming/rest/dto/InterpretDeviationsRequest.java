package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InterpretDeviationsRequest(@NotEmpty List<String> deviationTexts) {}
