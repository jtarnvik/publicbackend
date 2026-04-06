package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

public record StatisticsResponse(long routesShared, long aiInterpretationQueries, long userCount) {}
