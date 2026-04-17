package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

public record GtfsStatusResponse(
  String date,
  String status,
  String errorMessage,
  String downloadStartTime,
  String downloadEndTime,
  String unzipStartTime,
  String unzipEndTime,
  String parseStartTime,
  String parseEndTime
) {}
