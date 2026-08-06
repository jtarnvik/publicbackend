package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

/**
 * The remembered live traffic view, nested in {@link SettingsResponse} so the view can restore itself from
 * {@code /api/auth/me} without a fetch of its own. Null on the parent when nothing has been saved yet.
 */
public record LiveTrafficViewResponse(String transportMode, int routeGroup, Boolean focused) {}
