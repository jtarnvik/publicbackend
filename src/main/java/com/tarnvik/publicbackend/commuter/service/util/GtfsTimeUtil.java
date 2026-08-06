package com.tarnvik.publicbackend.commuter.service.util;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeInfo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Turns the clock times in {@code stop_times.txt} into real instants.
 * <p>
 * GTFS times are not clock times in the ordinary sense. They are measured from the <em>service</em>
 * midnight that starts the trip's service day, and hours run past 24 so that a journey departing at
 * 00:20 the following morning is written {@code 24:20:00} and still belongs to the day it set out on.
 * Two consequences drive this class: the hour field cannot be handed to {@link java.time.LocalTime}, and
 * the service date a trip belongs to has to be worked out rather than read off a clock.
 * <p>
 * The instant is built as <em>noon minus twelve hours</em> on the service date rather than as midnight.
 * That is the definition in the GTFS specification, and it is what makes the arithmetic survive a
 * daylight saving changeover: on the day the clocks move, midnight plus n seconds and noon minus twelve
 * hours plus n seconds are an hour apart, and the timetable means the latter.
 */
public class GtfsTimeUtil {
  private static final ZoneId ZONE = ZoneId.of("Europe/Stockholm");

  private static final int SECONDS_PER_HOUR = 3600;
  private static final int SECONDS_PER_MINUTE = 60;

  /**
   * Service days to consider when placing a trip in time. A trip observed shortly after midnight belongs
   * to yesterday's service day; one observed shortly before belongs to tomorrow's if the feed has already
   * rolled over. Anything further out than a day either side is not a live vehicle.
   */
  private static final List<Integer> CANDIDATE_DAY_OFFSETS = List.of(-1, 0, 1);

  private GtfsTimeUtil() {}

  /**
   * Parses a GTFS {@code HH:MM:SS} time into seconds since service midnight. The hour field may exceed
   * 23 and regularly does — see the class comment.
   *
   * @throws IllegalArgumentException if the value is missing or not three colon-separated numbers
   */
  public static int toSecondsSinceServiceMidnight(String gtfsTime) {
    if (gtfsTime == null || gtfsTime.isBlank()) {
      throw new IllegalArgumentException("GTFS time is missing");
    }
    String[] parts = gtfsTime.trim().split(":");
    if (parts.length != 3) {
      throw new IllegalArgumentException("Not a GTFS HH:MM:SS time: " + gtfsTime);
    }
    try {
      int hours = Integer.parseInt(parts[0]);
      int minutes = Integer.parseInt(parts[1]);
      int seconds = Integer.parseInt(parts[2]);
      return hours * SECONDS_PER_HOUR + minutes * SECONDS_PER_MINUTE + seconds;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Not a GTFS HH:MM:SS time: " + gtfsTime, e);
    }
  }

  /** The instant a scheduled time falls on, given the service day the trip belongs to. */
  public static Instant toInstant(LocalDate serviceDate, int secondsSinceServiceMidnight) {
    return serviceDate.atTime(12, 0)
      .atZone(ZONE)
      .minusHours(12)
      .plusSeconds(secondsSinceServiceMidnight)
      .toInstant();
  }

  /**
   * Works out which service day a trip observed at {@code observedAt} is running on, by trying the
   * candidate days and keeping the one whose scheduled span sits closest to the observation. A vehicle
   * inside its own timetable scores zero and wins outright; a late or early one is placed on the day it
   * misses by least.
   * <p>
   * This is deliberately independent of {@code calendar_dates.txt}. The vehicle is on the road, so the
   * question is not whether the service runs today but which midnight its times are counted from — and
   * the answer is legible from the times themselves.
   *
   * @param stopTimes the trip's own stop times, ordered by stop sequence
   * @throws IllegalArgumentException if the trip has no stop times
   */
  public static LocalDate resolveServiceDate(List<GtfsStopTimeInfo> stopTimes, Instant observedAt) {
    if (stopTimes == null || stopTimes.isEmpty()) {
      throw new IllegalArgumentException("Cannot resolve a service date for a trip with no stop times");
    }
    int firstDeparture = toSecondsSinceServiceMidnight(stopTimes.getFirst().getDepartureTime());
    int lastArrival = toSecondsSinceServiceMidnight(stopTimes.getLast().getArrivalTime());
    LocalDate observedDate = LocalDate.ofInstant(observedAt, ZONE);

    LocalDate best = null;
    long bestDistance = Long.MAX_VALUE;
    for (int offset : CANDIDATE_DAY_OFFSETS) {
      LocalDate candidate = observedDate.plusDays(offset);
      long distance = secondsOutsideSpan(
        toInstant(candidate, firstDeparture),
        toInstant(candidate, lastArrival),
        observedAt);
      if (distance < bestDistance) {
        best = candidate;
        bestDistance = distance;
      }
    }
    return best;
  }

  /** How far outside the span the instant falls, in seconds. Zero when it is inside. */
  private static long secondsOutsideSpan(Instant start, Instant end, Instant instant) {
    if (instant.isBefore(start)) {
      return start.getEpochSecond() - instant.getEpochSecond();
    }
    if (instant.isAfter(end)) {
      return instant.getEpochSecond() - end.getEpochSecond();
    }
    return 0;
  }
}
