package com.tarnvik.publicbackend.commuter.model.domain;

/**
 * A stop the user wants emphasised on the live traffic schematic.
 * <p>
 * {@code stopId} is a GTFS <b>parent station</b> id ({@code 9021001…}), which is what the live stop chain is
 * built from. Note that this is a different namespace from {@link RecentStop#stopPointId()} and
 * {@code UserSettings.stopPointId}, which are SL journey planner site ids — the two are not interchangeable
 * in either direction, which is why this is its own type rather than a reuse of {@link RecentStop}.
 * <p>
 * {@code stopName} is stored rather than looked up because the in-memory GTFS dataset is empty outside the
 * {@code local} profile: without it the settings dialog could not render an existing selection at all, and a
 * stop that leaves the timetable would become unidentifiable. It is a display fallback for the settings
 * dialog only — on the schematic the chain's own name wins.
 */
public record FavouriteStop(String stopId, String stopName) {}
