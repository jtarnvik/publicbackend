package com.tarnvik.publicbackend.commuter.service.util;

import com.tarnvik.publicbackend.commuter.model.gtfs.GtfsStopTimeInfo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The daylight saving cases are the reason this class exists, so they are asserted against real
 * changeover dates: the EU moves the clocks on the last Sunday of March and of October, which in 2026
 * falls on the 29th and the 25th. A timetabled 08:00 departure must land on 08:00 local on both days.
 */
class GtfsTimeUtilTest {
  private static final int EIGHT_TWENTYFOUR = 8 * 3600 + 24 * 60;

  private static GtfsStopTimeInfo stopTime(int sequence, String arrival, String departure) {
    return GtfsStopTimeInfo.builder()
      .stopSequence(sequence)
      .arrivalTime(arrival)
      .departureTime(departure)
      .build();
  }

  @Test
  void parsesAnOrdinaryTime() {
    assertThat(GtfsTimeUtil.toSecondsSinceServiceMidnight("08:24:00")).isEqualTo(EIGHT_TWENTYFOUR);
  }

  @Test
  void parsesAnHourPastTwentyFour() {
    assertThat(GtfsTimeUtil.toSecondsSinceServiceMidnight("25:10:30")).isEqualTo(25 * 3600 + 10 * 60 + 30);
  }

  @Test
  void rejectsAMissingTime() {
    assertThatThrownBy(() -> GtfsTimeUtil.toSecondsSinceServiceMidnight(null))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GtfsTimeUtil.toSecondsSinceServiceMidnight("  "))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAMalformedTime() {
    assertThatThrownBy(() -> GtfsTimeUtil.toSecondsSinceServiceMidnight("08:24"))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GtfsTimeUtil.toSecondsSinceServiceMidnight("kvart över åtta"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void placesAnOrdinarySummerTime() {
    Instant result = GtfsTimeUtil.toInstant(LocalDate.of(2026, 8, 6), EIGHT_TWENTYFOUR);

    assertThat(result).isEqualTo(Instant.parse("2026-08-06T06:24:00Z"));
  }

  @Test
  void placesATimePastMidnightOnTheFollowingCalendarDay() {
    int twentyFourTwenty = 24 * 3600 + 20 * 60;

    Instant result = GtfsTimeUtil.toInstant(LocalDate.of(2026, 8, 6), twentyFourTwenty);

    assertThat(result).isEqualTo(Instant.parse("2026-08-06T22:20:00Z"));
  }

  @Test
  void keepsTheWallClockOnTheSpringForwardDay() {
    Instant result = GtfsTimeUtil.toInstant(LocalDate.of(2026, 3, 29), 8 * 3600);

    // 08:00 CEST — an hour earlier in UTC than the same wall clock the day before.
    assertThat(result).isEqualTo(Instant.parse("2026-03-29T06:00:00Z"));
  }

  @Test
  void keepsTheWallClockOnTheFallBackDay() {
    Instant result = GtfsTimeUtil.toInstant(LocalDate.of(2026, 10, 25), 8 * 3600);

    // 08:00 CET — the clocks went back at 03:00, so this is an hour later in UTC than the day before.
    assertThat(result).isEqualTo(Instant.parse("2026-10-25T07:00:00Z"));
  }

  @Test
  void resolvesADaytimeTripToTheDayItIsObservedOn() {
    List<GtfsStopTimeInfo> stopTimes = List.of(
      stopTime(1, "08:24:00", "08:24:00"),
      stopTime(2, "09:39:00", "09:39:00"));

    LocalDate result = GtfsTimeUtil.resolveServiceDate(stopTimes, Instant.parse("2026-08-06T07:00:00Z"));

    assertThat(result).isEqualTo(LocalDate.of(2026, 8, 6));
  }

  @Test
  void resolvesATripSeenAfterMidnightToTheServiceDayItSetOutOn() {
    List<GtfsStopTimeInfo> stopTimes = List.of(
      stopTime(1, "23:40:00", "23:40:00"),
      stopTime(2, "24:20:00", "24:20:00"));

    // 00:10 local on the 7th, so the calendar day has rolled over but the service day has not.
    LocalDate result = GtfsTimeUtil.resolveServiceDate(stopTimes, Instant.parse("2026-08-06T22:10:00Z"));

    assertThat(result).isEqualTo(LocalDate.of(2026, 8, 6));
  }

  @Test
  void resolvesALateRunningTripToTheDayItMissesByLeast() {
    List<GtfsStopTimeInfo> stopTimes = List.of(
      stopTime(1, "08:24:00", "08:24:00"),
      stopTime(2, "09:39:00", "09:39:00"));

    // Half an hour past its scheduled arrival — still far closer to today than to either neighbour.
    LocalDate result = GtfsTimeUtil.resolveServiceDate(stopTimes, Instant.parse("2026-08-06T08:09:00Z"));

    assertThat(result).isEqualTo(LocalDate.of(2026, 8, 6));
  }

  @Test
  void rejectsATripWithNoStopTimes() {
    assertThatThrownBy(() -> GtfsTimeUtil.resolveServiceDate(List.of(), Instant.now()))
      .isInstanceOf(IllegalArgumentException.class);
  }
}
