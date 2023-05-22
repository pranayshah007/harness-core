/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

public class DateTimeUtils {
  private DateTimeUtils() {}

  public static Instant roundDownTo5MinBoundary(Instant instant) {
    return roundDownToMinBoundary(instant, 5);
  }

  public static Instant roundDownTo1MinBoundary(Instant instant) {
    return roundDownToMinBoundary(instant, 1);
  }

  public static Instant roundDownToMinBoundary(Instant instant, int minBoundary) {
    Preconditions.checkArgument(minBoundary > 0 && minBoundary < 60, "Minute boundary need to be between 1 to 59");
    ZonedDateTime zonedDateTime = instant.atZone(ZoneOffset.UTC);
    int minute = zonedDateTime.getMinute();
    minute = minute - minute % minBoundary;
    zonedDateTime = ZonedDateTime.of(zonedDateTime.getYear(), zonedDateTime.getMonthValue(),
        zonedDateTime.getDayOfMonth(), zonedDateTime.getHour(), minute, 0, 0, ZoneOffset.UTC);
    return zonedDateTime.toInstant();
  }

  public static Instant roundUpTo5MinBoundary(Instant instant) {
    return roundUpToMinBoundary(instant, 5);
  }

  public static Instant roundUpToMinBoundary(Instant instant, int minBoundary) {
    Preconditions.checkArgument(minBoundary > 0 && minBoundary < 60, "Minute boundary need to be between 1 to 59");
    Instant roundDownToMinBoundary = roundDownToMinBoundary(instant, minBoundary);
    if (!roundDownToMinBoundary.equals(instant)) {
      roundDownToMinBoundary = roundDownToMinBoundary.plus(5, ChronoUnit.MINUTES);
    }
    return roundDownToMinBoundary;
  }

  public static long instantToEpochMinute(Instant instant) {
    return TimeUnit.MILLISECONDS.toMinutes(instant.toEpochMilli());
  }
  public static Instant epochMinuteToInstant(long epochMinute) {
    return Instant.ofEpochSecond(TimeUnit.MINUTES.toSeconds(epochMinute));
  }
}
