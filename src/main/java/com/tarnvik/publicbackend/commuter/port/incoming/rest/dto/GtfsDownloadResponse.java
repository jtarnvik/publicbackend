package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

public record GtfsDownloadResponse(boolean skipped, long fileSizeBytes, Long downloadDurationMs) {
}
