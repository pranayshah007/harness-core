/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static java.lang.String.format;

import io.harness.exception.InvalidRequestException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TimeStampUtils {
  private DateTimeFormatter dtf = new DateTimeFormatterBuilder()
                                      .parseCaseInsensitive()
                                      .appendPattern("yyyy-MM-dd hh:mm a")
                                      .toFormatter(Locale.ENGLISH);

  public Long getTotalDurationWRTCurrentTimeFromTimeStamp(String timestamp, String timezone) {
    Long startTime = Instant.now().toEpochMilli();
    Long endTime = getEpochValueFromDateString(timestamp, timezone);
    return endTime - startTime;
  }

  private Long getEpochValueFromDateString(String dateTime, String timezone) {
    LocalDateTime date = getLocalDateFromDateString(dateTime);
    return getEpochValueFromLocalTime(date, timezone);
  }

  private LocalDateTime getLocalDateFromDateString(String date) {
    try {
      return LocalDateTime.parse(date, dtf);
    } catch (DateTimeParseException e) {
      throw new InvalidRequestException("Invalid date format. Please use the format: yyyy-MM-dd hh:mm a");
    }
  }

  private Long getEpochValueFromLocalTime(LocalDateTime date, String timezone) {
    try {
      ZoneOffset zoneOffset = ZoneId.of(timezone).getRules().getOffset(date);
      return date.toInstant(zoneOffset).toEpochMilli();
    } catch (DateTimeParseException e) {
      throw new InvalidRequestException(format("Invalid timezone : %s", e.getMessage()));
    }
  }
}
