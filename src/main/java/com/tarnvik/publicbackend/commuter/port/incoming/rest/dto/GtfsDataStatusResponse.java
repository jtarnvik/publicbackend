package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

public record GtfsDataStatusResponse(String date, String status, boolean staticDataAvailable) {}
