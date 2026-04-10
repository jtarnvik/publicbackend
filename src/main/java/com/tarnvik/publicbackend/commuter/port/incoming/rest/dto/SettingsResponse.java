package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import com.tarnvik.publicbackend.commuter.model.domain.RecentStop;

import java.util.List;

public record SettingsResponse(String stopPointId, String stopPointName, boolean useAiInterpretation, List<RecentStop> recentStops) {}
