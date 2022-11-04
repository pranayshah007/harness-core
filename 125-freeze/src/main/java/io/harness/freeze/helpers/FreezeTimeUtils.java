/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.helpers;

import io.harness.freeze.beans.CurrentOrUpcomingWindow;
import io.harness.freeze.beans.FreezeDuration;
import io.harness.freeze.beans.FreezeWindow;
import io.harness.freeze.beans.RecurrenceType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import lombok.experimental.UtilityClass;

@UtilityClass
public class FreezeTimeUtils {
  DateTimeFormatter dtf = new DateTimeFormatterBuilder()
                              .parseCaseInsensitive()
                              .appendPattern("yyyy-MM-dd hh:mm a")
                              .toFormatter(Locale.ENGLISH);
  LocalDateTime now = LocalDateTime.now();

  public CurrentOrUpcomingWindow fetchCurrentOrUpcomingTimeWindow(List<FreezeWindow> freezeWindows) {
    List<CurrentOrUpcomingWindow> currentOrUpcomingWindows = new LinkedList<>();
    if (freezeWindows == null) {
      return null;
    }
    freezeWindows.stream().forEach(freezeWindow -> {
      CurrentOrUpcomingWindow currentOrUpcomingWindow = fetchCurrentOrUpcomingTimeWindow(freezeWindow);
      currentOrUpcomingWindows.add(currentOrUpcomingWindow);
    });
    // Later when more windows are supported write algo that will read through all the pairs and find the first max pair
    return currentOrUpcomingWindows.get(0);
  }
  private CurrentOrUpcomingWindow fetchCurrentOrUpcomingTimeWindow(FreezeWindow freezeWindow) {
    TimeZone timeZone = TimeZone.getTimeZone(freezeWindow.getTimeZone());
    LocalDateTime firstWindowStartTime = LocalDateTime.parse(freezeWindow.getStartTime(), dtf);
    LocalDateTime firstWindowEndTime;
    if (freezeWindow.getEndTime() == null) {
      FreezeDuration freezeDuration = FreezeDuration.fromString(freezeWindow.getDuration());
      Long endTime = getEpochValueFromDateString(firstWindowStartTime, timeZone) + freezeDuration.getTimeoutInMillis();
      firstWindowEndTime = Instant.ofEpochMilli(endTime).atZone(timeZone.toZoneId()).toLocalDateTime();
    } else {
      firstWindowEndTime = LocalDateTime.parse(freezeWindow.getEndTime(), dtf);
    }
    if (freezeWindow.getRecurrence() == null) {
      if (getCurrentTime() > getEpochValueFromDateString(firstWindowEndTime, timeZone)) {
        return null;
      } else {
        return CurrentOrUpcomingWindow.builder()
            .startTime(getEpochValueFromDateString(firstWindowStartTime, timeZone))
            .endTime(getEpochValueFromDateString(firstWindowEndTime, timeZone))
            .build();
      }
    } else {
      LocalDateTime until;
      if (freezeWindow.getRecurrence().getSpec() == null) {
        until = getLocalDateTime(RecurrenceType.YEARLY, firstWindowEndTime, 5);
      } else {
        until = LocalDateTime.parse(freezeWindow.getRecurrence().getSpec().getUntil(), dtf);
      }
      Long lastWindowEndTimeEpoch = getEpochValueFromDateString(until, timeZone);
      if (getCurrentTime() > lastWindowEndTimeEpoch) {
        return null;
      } else {
        return fetchCurrentOrUpcomingTimeWindow(firstWindowStartTime, firstWindowEndTime, lastWindowEndTimeEpoch,
            freezeWindow.getRecurrence().getRecurrenceType(), timeZone);
      }
    }
  }

  private CurrentOrUpcomingWindow fetchCurrentOrUpcomingTimeWindow(LocalDateTime firstWindowStartTime,
      LocalDateTime firstWindowEndTime, Long lastWindowEndTimeEpoch, RecurrenceType recurrenceType, TimeZone timeZone) {
    int recurrenceNumber = 0;
    Long currentWindowStartTime = getEpochValue(recurrenceType, firstWindowStartTime, timeZone, recurrenceNumber);
    Long currentWindowEndTime = getEpochValue(recurrenceType, firstWindowEndTime, timeZone, recurrenceNumber);
    while (currentWindowStartTime < lastWindowEndTimeEpoch) {
      if (currentWindowIsActive(currentWindowStartTime, currentWindowEndTime)
          || getCurrentTime() < currentWindowStartTime) {
        return CurrentOrUpcomingWindow.builder()
            .startTime(currentWindowStartTime)
            .endTime(Math.min(currentWindowEndTime, lastWindowEndTimeEpoch))
            .build();
      }
      recurrenceNumber++;
      currentWindowStartTime = getEpochValue(recurrenceType, firstWindowStartTime, timeZone, recurrenceNumber);
      currentWindowEndTime = getEpochValue(recurrenceType, firstWindowEndTime, timeZone, recurrenceNumber);
    }
    return null;
  }

  private boolean currentWindowIsActive(Long windowStartTime, Long windowEndTime) {
    Long currentTime = getCurrentTime();
    return currentTime > windowStartTime && currentTime < windowEndTime;
  }

  public boolean currentWindowIsActive(CurrentOrUpcomingWindow currentOrUpcomingWindow) {
    return currentOrUpcomingWindow != null
        && currentWindowIsActive(currentOrUpcomingWindow.getStartTime(), currentOrUpcomingWindow.getEndTime());
  }

  private Long getEpochValue(RecurrenceType offsetType, LocalDateTime date, TimeZone timeZone, int offsetValue) {
    ZoneOffset zoneOffset = timeZone.toZoneId().getRules().getOffset(now);
    if (offsetValue == 0) {
      return date.toInstant(zoneOffset).toEpochMilli();
    }
    switch (offsetType) {
      case DAILY:
        return date.plusDays(offsetValue).toInstant(zoneOffset).toEpochMilli();
      case WEEKLY:
        return date.plusWeeks(offsetValue).toInstant(zoneOffset).toEpochMilli();
      case MONTHLY:
        return date.plusMonths(offsetValue).toInstant(zoneOffset).toEpochMilli();
      case YEARLY:
        return date.plusYears(offsetValue).toInstant(zoneOffset).toEpochMilli();
      default:
        return date.toInstant(zoneOffset).toEpochMilli();
    }
  }

  private LocalDateTime getLocalDateTime(RecurrenceType offsetType, LocalDateTime date, int offsetValue) {
    switch (offsetType) {
      case DAILY:
        return date.plusDays(offsetValue);
      case WEEKLY:
        return date.plusWeeks(offsetValue);
      case MONTHLY:
        return date.plusMonths(offsetValue);
      case YEARLY:
        return date.plusYears(offsetValue);
      default:
        return date;
    }
  }

  private static long getCurrentTime() {
    return new Date().getTime();
  }

  private Long getEpochValueFromDateString(LocalDateTime date, TimeZone timeZone) {
    if (date == null) {
      return null;
    }

    ZoneOffset zoneOffset = timeZone.toZoneId().getRules().getOffset(now);
    return date.toInstant(zoneOffset).toEpochMilli();
  }
}
