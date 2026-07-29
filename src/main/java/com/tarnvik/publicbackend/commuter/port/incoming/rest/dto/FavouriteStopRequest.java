package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

/**
 * One favourite stop in a settings save. Deliberately unvalidated — see {@link SettingsRequest} for why a
 * malformed element must not fail the whole save. Blank ids are dropped in the service instead.
 */
public record FavouriteStopRequest(String stopId, String stopName) {}
