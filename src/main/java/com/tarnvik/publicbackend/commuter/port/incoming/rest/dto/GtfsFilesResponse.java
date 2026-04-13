package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import java.util.List;

public record GtfsFilesResponse(List<GtfsFileInfo> files) {
}
