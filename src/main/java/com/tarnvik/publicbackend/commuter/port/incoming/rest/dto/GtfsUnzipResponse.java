package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import java.util.List;

public record GtfsUnzipResponse(List<GtfsFileInfo> files, long unzipDurationMs) {
}
