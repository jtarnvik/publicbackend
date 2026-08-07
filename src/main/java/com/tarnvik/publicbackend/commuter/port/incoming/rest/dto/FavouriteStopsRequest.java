package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The favourite stops on their own, saved by the live traffic view when a stop is tapped on the schematic.
 * <p>
 * <b>{@code favouriteStops} is {@code @NotNull} here, unlike in {@link SettingsRequest}.</b> There it has to
 * tolerate null because a user on a cached older bundle sends no such field; this endpoint is new, so every
 * caller of it is new too, and an absent list can only be a mistake. The elements stay unvalidated — the
 * service drops blanks rather than rejecting the request.
 */
public record FavouriteStopsRequest(
  @NotNull List<FavouriteStopRequest> favouriteStops
) {}
