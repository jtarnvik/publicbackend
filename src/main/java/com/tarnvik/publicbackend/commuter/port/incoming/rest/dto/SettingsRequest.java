package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * A settings save from the dialog. Everything the dialog owns is sent in one request, so a save cannot
 * half-succeed.
 * <p>
 * <b>{@code favouriteStops} is null-tolerant on purpose:</b> null means "leave the stored list unchanged",
 * an empty list means "clear it". The frontend is a static bundle on GitHub Pages, so a user on a cached
 * older bundle sends no {@code favouriteStops} at all — with {@code @NotNull} their entire settings save
 * would fail and they could no longer even change their stop. The elements are unvalidated for the same
 * reason; the service drops blanks rather than rejecting the request.
 */
public record SettingsRequest(
  @NotBlank String stopPointId,
  @NotBlank String stopPointName,
  boolean useAiInterpretation,
  List<FavouriteStopRequest> favouriteStops
) {}
