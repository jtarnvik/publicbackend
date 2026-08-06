package com.tarnvik.publicbackend.commuter.port.incoming.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The live traffic view's state, saved whenever the user changes it. Deliberately its own endpoint rather
 * than a field on {@link SettingsRequest}: that one is owned by the settings dialog and requires a stop
 * point the live traffic view has no business supplying, and keeping the two apart means neither can
 * overwrite the other's columns.
 * <p>
 * {@code focused} is nullable — a group whose focus switch is locked (the metro, which must stay focused,
 * and the buses, which have no window) sends null so that selecting it cannot overwrite the remembered
 * flag of a group where the switch does something.
 */
public record LiveTrafficViewRequest(
  @NotBlank String transportMode,
  int routeGroup,
  Boolean focused
) {}
