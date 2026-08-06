package com.tarnvik.publicbackend.commuter.port.incoming.rest.mapper;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class DateConverter {
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  @DateFormat
  public String formatDateTime(LocalDateTime dateTime) {
    if (dateTime == null) {
      return null;
    }
    return dateTime.format(DATE_TIME_FORMATTER);
  }

  @LocalDateFormat
  public String formatDate(LocalDate date) {
    if (date == null) {
      return null;
    }
    return date.format(DATE_FORMATTER);
  }

  /**
   * An instant as epoch seconds, for times the browser is going to do arithmetic on rather than display.
   * Zero for null, which no caller can produce today — the alternative is a boxed {@code Long} that every
   * consumer would then have to null-check.
   */
  @EpochSeconds
  public long toEpochSeconds(Instant instant) {
    if (instant == null) {
      return 0;
    }
    return instant.getEpochSecond();
  }
}
