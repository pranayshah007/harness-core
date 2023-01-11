/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeParser {
  private DateTimeParser() {}
  public static final String FORMAT = "E:dd:MMM:yyyy:HH:mm";

  public static long getEpochSecond(String dateTime, String timezoneStr) {
    return getEpochSecond(dateTime, FORMAT, timezoneStr);
  }

  public static long getEpochSecond(String dateTime, String pattern, String timezoneStr) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
    LocalDateTime localDateTime = LocalDateTime.parse(dateTime, formatter);
    ZoneId zone = ZoneId.of(timezoneStr);
    ZonedDateTime zonedDateTime = ZonedDateTime.of(localDateTime, zone);
    return zonedDateTime.toInstant().getEpochSecond();
  }

  public static String getDateTime(long epochTimestamp, String timezoneStr) {
    Instant instant = Instant.ofEpochSecond(epochTimestamp);
    ZoneId zone = ZoneId.of(timezoneStr);
    return instant.atZone(zone).format(DateTimeFormatter.ofPattern(FORMAT));
  }
}
