package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import com.tarnvik.publicbackend.commuter.model.domain.entity.Importance;

public record DeviationInterpretationResult(Long id, Importance importance, DeviationAction action, Boolean delays, Boolean cancelations) {}
