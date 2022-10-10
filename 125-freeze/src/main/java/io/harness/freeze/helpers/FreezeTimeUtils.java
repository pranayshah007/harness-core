/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.helpers;

import io.harness.freeze.beans.CurrentOrUpcomingActiveWindow;
import io.harness.freeze.beans.FreezeWindow;
import io.harness.freeze.beans.RecurrenceType;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import lombok.experimental.UtilityClass;

@UtilityClass
public class FreezeTimeUtils {
  DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");
  LocalDateTime now = LocalDateTime.now();

  public CurrentOrUpcomingActiveWindow fetchCurrentOrUpcomingTimeWindow(List<FreezeWindow> freezeWindows) {
    List<CurrentOrUpcomingActiveWindow> currentOrUpcomingActiveWindows = new LinkedList<>();
    if (freezeWindows == null) {
      return null;
    }
    freezeWindows.stream().forEach(freezeWindow -> {
      CurrentOrUpcomingActiveWindow currentOrUpcomingActiveWindow = fetchCurrentOrUpcomingTimeWindow(freezeWindow);
      currentOrUpcomingActiveWindows.add(currentOrUpcomingActiveWindow);
    });
    // Later when more windows are supported write algo that will read through all the pairs and find the first max pair
    return currentOrUpcomingActiveWindows.get(0);
  }
  private CurrentOrUpcomingActiveWindow fetchCurrentOrUpcomingTimeWindow(FreezeWindow freezeWindow) {
    TimeZone timeZone = TimeZone.getTimeZone(freezeWindow.getTimeZone());
    String firstWindowStartTime = freezeWindow.getStartTime();
    String firstWindowEndTime = freezeWindow.getEndTime();
    if (freezeWindow.getRecurrence() == null) {
      if (getCurrentTime() > getEpochValueFromDateString(freezeWindow.getEndTime(), timeZone)) {
        return null;
      } else {
        return CurrentOrUpcomingActiveWindow.builder()
            .startTime(getEpochValueFromDateString(freezeWindow.getStartTime(), timeZone))
            .endTime(getEpochValueFromDateString(freezeWindow.getEndTime(), timeZone))
            .build();
      }
    } else {
      Long lastWindowEndTimeEpoch =
          getEpochValueFromDateString(freezeWindow.getRecurrence().getSpec().getUntil(), timeZone);
      if (getCurrentTime() > lastWindowEndTimeEpoch) {
        return null;
      } else {
        return fetchCurrentOrUpcomingTimeWindow(firstWindowStartTime, firstWindowEndTime, lastWindowEndTimeEpoch,
            freezeWindow.getRecurrence().getRecurrenceType(), timeZone);
      }
    }
  }

  private CurrentOrUpcomingActiveWindow fetchCurrentOrUpcomingTimeWindow(String firstWindowStartTime,
      String firstWindowEndTime, Long lastWindowEndTimeEpoch, RecurrenceType recurrenceType, TimeZone timeZone) {
    int recurrenceNumber = 0;
    Long currentWindowStartTime = getEpochValue(recurrenceType, firstWindowStartTime, timeZone, recurrenceNumber);
    Long currentWindowEndTime = getEpochValue(recurrenceType, firstWindowEndTime, timeZone, recurrenceNumber);
    while (currentWindowStartTime < lastWindowEndTimeEpoch) {
      if (currentWindowIsActive(currentWindowStartTime, currentWindowEndTime)
          || getCurrentTime() < currentWindowStartTime) {
        return CurrentOrUpcomingActiveWindow.builder()
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

  public boolean currentWindowIsActive(CurrentOrUpcomingActiveWindow currentOrUpcomingActiveWindow) {
    return currentOrUpcomingActiveWindow != null
        && currentWindowIsActive(
            currentOrUpcomingActiveWindow.getStartTime(), currentOrUpcomingActiveWindow.getEndTime());
  }

  private Long getEpochValue(RecurrenceType offsetType, String dateString, TimeZone timeZone, int offsetValue) {
    LocalDateTime zdt = LocalDateTime.parse(dateString, dtf);
    ZoneOffset zoneOffset = timeZone.toZoneId().getRules().getOffset(now);
    if (offsetValue == 0) {
      return zdt.toInstant(zoneOffset).toEpochMilli();
    }
    switch (offsetType) {
      case DAILY:
        return zdt.plusDays(offsetValue).toInstant(zoneOffset).toEpochMilli();
      case WEEKLY:
        return zdt.plusWeeks(offsetValue).toInstant(zoneOffset).toEpochMilli();
      case MONTHLY:
        return zdt.plusMonths(offsetValue).toInstant(zoneOffset).toEpochMilli();
      case YEARLY:
        return zdt.plusYears(offsetValue).toInstant(zoneOffset).toEpochMilli();
      default:
        return zdt.toInstant(zoneOffset).toEpochMilli();
    }
  }

  private static long getCurrentTime() {
    return new Date().getTime();
  }

  private Long getEpochValueFromDateString(String dateString, TimeZone timeZone) {
    if (dateString == null) {
      return null;
    }
    LocalDateTime zdt = LocalDateTime.parse(dateString, dtf);
    ZoneOffset zoneOffset = timeZone.toZoneId().getRules().getOffset(now);
    return zdt.toInstant(zoneOffset).toEpochMilli();
  }
}
