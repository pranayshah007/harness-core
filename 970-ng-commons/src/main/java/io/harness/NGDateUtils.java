/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.DX;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import lombok.experimental.UtilityClass;

@OwnedBy(DX)
@UtilityClass
public class NGDateUtils {
  public static final long HOUR_IN_MS = 60 * 60 * 1000;
  public static final long DAY_IN_MS = 24 * HOUR_IN_MS;
  public static final long MIN_DAYS_IN_YEAR = 365;
  public static final long MIN_DAYS_IN_6MONTHS = 182;
  public static final long MIN_DAYS_IN_MONTH = 28;
  public static final String YEAR_MONTH_DAY_DATE_PATTERN = "yyyy-MM-dd";

  public static long getStartTimeOfTheDayAsEpoch(long epoch) {
    return epoch - epoch % DAY_IN_MS;
  }

  /** 00:00:00 is considered as start time of a day */
  public static long getStartTimeOfNextDay(long epoch) {
    return getStartTimeOfTheDayAsEpoch(epoch) + DAY_IN_MS;
  }

  public static long getNumberOfDays(long start, long end) {
    return (long) Math.ceil((end - start) / (double) DAY_IN_MS);
  }

  public static long getStartTimeOfPreviousInterval(long epoch, long numberOfDays) {
    return epoch - (numberOfDays * DAY_IN_MS);
  }

  public static long getEndTimeOfPreviousInterval(long epoch) {
    return epoch - DAY_IN_MS;
  }

  // Returns starting midnight timestamp for next day for input timestamp
  // If input timestamp is midnight, then returns it as it is
  // eg: if input is 2nd Feb, 2020 01:10:00 UTC
  // o/p: 3rd Feb, 2020 00:00:00 UTC
  public static long getNextNearestWholeDayUTC(long epochInMs) {
    if (isTimestampMidnight(epochInMs)) {
      return epochInMs;
    }

    return getStartTimeOfNextDay(epochInMs);
  }

  public static LocalDate getLocalDateOrThrow(final String datePattern, final String date) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(datePattern, Locale.ENGLISH);
    try {
      return LocalDate.parse(date, formatter);
    } catch (DateTimeParseException e) {
      throw new InvalidArgumentsException(format("Invalid date format, pattern: %s, date: %s", datePattern, date));
    }
  }

  public static LocalDate getCurrentMonthFirstDay() {
    return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
  }

  // ----------------------- PRIVATE METHODS ---------------------------

  private static boolean isTimestampMidnight(long epochInMs) {
    return epochInMs % DAY_IN_MS == 0;
  }

  public static double getDiffOfTimeStampsInMinutes(Long currentTs, Long previousTs) {
    return Math.ceil((currentTs - previousTs) / 60000);
  }
}
