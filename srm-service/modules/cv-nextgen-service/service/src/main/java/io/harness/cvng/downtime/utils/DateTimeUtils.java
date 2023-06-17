/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.downtime.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.TimeZone;

public class DateTimeUtils {
  public static DateTimeFormatter dtf = new DateTimeFormatterBuilder()
                                            .parseCaseInsensitive()
                                            .appendPattern("yyyy-MM-dd hh:mm a")
                                            .toFormatter(Locale.ENGLISH);

  public static LocalDateTime getLocalDateFromDateString(String date) {
    try {
      return LocalDateTime.parse(date, dtf);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Invalid date format. Please use the format: yyyy-MM-dd hh:mm a");
    }
  }

  public static Long getEpochValueFromLocalTime(LocalDateTime date, String timezone) {
    ZoneOffset zoneOffset = TimeZone.getTimeZone(timezone).toZoneId().getRules().getOffset(date);
    return date.toInstant(zoneOffset).getEpochSecond();
  }

  public static long getEpochValueFromDateString(String dateTime, String timezone) {
    LocalDateTime date = getLocalDateFromDateString(dateTime);
    return getEpochValueFromLocalTime(date, timezone);
  }

  public static String getDateStringFromEpoch(long epochSeconds, String timezone) {
    LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.of(timezone));
    return dtf.format(dateTime);
  }
}
