package com.tarnvik.publicbackend.commuter.model.gtfs.livetraffic;

import com.tarnvik.publicbackend.commuter.model.domain.entity.TransportMode;

public record GroupKey(TransportMode transportMode, int routeGroup) {
}
