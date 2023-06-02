/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.helpers;

import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.freeze.beans.CurrentOrUpcomingWindow;
import io.harness.freeze.beans.FreezeDuration;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeWindow;
import io.harness.freeze.beans.Recurrence;
import io.harness.freeze.beans.RecurrenceSpec;
import io.harness.freeze.beans.RecurrenceType;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Pair;

@UtilityClass
public class FreezeTimeUtils {
  public DateTimeFormatter dtf = new DateTimeFormatterBuilder()
                                     .parseCaseInsensitive()
                                     .appendPattern("yyyy-MM-dd hh:mm a")
                                     .toFormatter(Locale.ENGLISH);
  LocalDateTime now = LocalDateTime.now();

  private static final long MIN_FREEZE_WINDOW_TIME = 1800000L;
  private static final long MAX_FREEZE_WINDOW_TIME = 31536000000L;
  private static final long MAX_FREEZE_START_TIME = 157680000000L;

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
    LocalDateTime firstWindowEndTime = getLocalDateTime(freezeWindow, timeZone, firstWindowStartTime);
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
        if (freezeWindow.getRecurrence().getSpec().getUntil() == null) {
          until = getLocalDateTime(RecurrenceType.YEARLY, firstWindowEndTime, 5);
        } else {
          until = LocalDateTime.parse(freezeWindow.getRecurrence().getSpec().getUntil(), dtf);
        }
      }
      Long lastWindowEndTimeEpoch = getEpochValueFromDateString(until, timeZone);
      if (getCurrentTime() > lastWindowEndTimeEpoch) {
        return null;
      } else {
        return fetchCurrentOrUpcomingTimeWindow(firstWindowStartTime, firstWindowEndTime, lastWindowEndTimeEpoch,
            freezeWindow.getRecurrence().getRecurrenceType(), timeZone, freezeWindow.getRecurrence().getSpec());
      }
    }
  }

  private CurrentOrUpcomingWindow fetchCurrentOrUpcomingTimeWindow(LocalDateTime firstWindowStartTime,
      LocalDateTime firstWindowEndTime, Long lastWindowEndTimeEpoch, RecurrenceType recurrenceType, TimeZone timeZone,
      RecurrenceSpec recurrenceSpec) {
    int recurrenceNumber = 0;
    int valueMultiplier = 1;
    if (recurrenceSpec != null) {
      if (recurrenceSpec.getValue() != null) {
        valueMultiplier = recurrenceSpec.getValue();
      }
    }
    Long currentWindowStartTime =
        getEpochValue(recurrenceType, firstWindowStartTime, timeZone, valueMultiplier * recurrenceNumber);
    Long currentWindowEndTime =
        getEpochValue(recurrenceType, firstWindowEndTime, timeZone, valueMultiplier * recurrenceNumber);
    while (currentWindowStartTime < lastWindowEndTimeEpoch) {
      if (currentWindowIsActive(currentWindowStartTime, currentWindowEndTime)
          || getCurrentTime() < currentWindowStartTime) {
        return CurrentOrUpcomingWindow.builder()
            .startTime(currentWindowStartTime)
            .endTime(Math.min(currentWindowEndTime, lastWindowEndTimeEpoch))
            .build();
      }
      recurrenceNumber++;
      currentWindowStartTime =
          getEpochValue(recurrenceType, firstWindowStartTime, timeZone, valueMultiplier * recurrenceNumber);
      currentWindowEndTime =
          getEpochValue(recurrenceType, firstWindowEndTime, timeZone, valueMultiplier * recurrenceNumber);
    }
    return null;
  }

  public List<Long> fetchUpcomingTimeWindow(List<FreezeWindow> freezeWindows) {
    List<Long> allUpcomingWindows = new LinkedList<>();
    if (freezeWindows == null) {
      return allUpcomingWindows;
    }
    freezeWindows.forEach(freezeWindow -> {
      List<Long> upcomingWindows = fetchUpcomingTimeWindow(freezeWindow);
      allUpcomingWindows.addAll(upcomingWindows);
    });
    // TODO : Sort all upcoming start time in case of multiple windows in future
    return allUpcomingWindows;
  }

  private List<Long> fetchUpcomingTimeWindow(FreezeWindow freezeWindow) {
    TimeZone timeZone = TimeZone.getTimeZone(freezeWindow.getTimeZone());
    LocalDateTime firstWindowStartTime = LocalDateTime.parse(freezeWindow.getStartTime(), dtf);
    LocalDateTime firstWindowEndTime = getLocalDateTime(freezeWindow, timeZone, firstWindowStartTime);
    if (freezeWindow.getRecurrence() == null) {
      return fetchUpcomingTimeWindowWithoutRecurrence(timeZone, firstWindowStartTime, firstWindowEndTime);
    } else {
      return fetchUpcomingTimeWindowWithRecurrence(freezeWindow, timeZone, firstWindowStartTime, firstWindowEndTime);
    }
  }

  private List<Long> fetchUpcomingTimeWindowWithRecurrence(FreezeWindow freezeWindow, TimeZone timeZone,
      LocalDateTime firstWindowStartTime, LocalDateTime firstWindowEndTime) {
    LocalDateTime until;
    if (freezeWindow.getRecurrence().getSpec() == null) {
      until = getLocalDateTime(RecurrenceType.YEARLY, firstWindowEndTime, 5);
    } else {
      if (freezeWindow.getRecurrence().getSpec().getUntil() == null) {
        until = getLocalDateTime(RecurrenceType.YEARLY, firstWindowEndTime, 5);
      } else {
        until = LocalDateTime.parse(freezeWindow.getRecurrence().getSpec().getUntil(), dtf);
      }
    }
    Long lastWindowEndTimeEpoch = getEpochValueFromDateString(until, timeZone);
    if (getCurrentTime() > lastWindowEndTimeEpoch) {
      return new ArrayList<>();
    } else {
      return fetchUpcomingTimeWindow(firstWindowStartTime, firstWindowEndTime, lastWindowEndTimeEpoch,
          freezeWindow.getRecurrence().getRecurrenceType(), timeZone, freezeWindow.getRecurrence().getSpec());
    }
  }

  private List<Long> fetchUpcomingTimeWindowWithoutRecurrence(
      TimeZone timeZone, LocalDateTime firstWindowStartTime, LocalDateTime firstWindowEndTime) {
    if (getCurrentTime() <= getEpochValueFromDateString(firstWindowEndTime, timeZone)) {
      if (getCurrentTime() < getEpochValueFromDateString(firstWindowStartTime, timeZone)) {
        return Collections.singletonList(getEpochValueFromDateString(firstWindowStartTime, timeZone));
      }
    }
    return new ArrayList<>();
  }

  private LocalDateTime getLocalDateTime(
      FreezeWindow freezeWindow, TimeZone timeZone, LocalDateTime firstWindowStartTime) {
    LocalDateTime firstWindowEndTime;
    if (freezeWindow.getEndTime() == null) {
      FreezeDuration freezeDuration = FreezeDuration.fromString(freezeWindow.getDuration());
      Long endTime = getEpochValueFromDateString(firstWindowStartTime, timeZone) + freezeDuration.getTimeoutInMillis();
      firstWindowEndTime = Instant.ofEpochMilli(endTime).atZone(timeZone.toZoneId()).toLocalDateTime();
    } else {
      firstWindowEndTime = LocalDateTime.parse(freezeWindow.getEndTime(), dtf);
    }
    return firstWindowEndTime;
  }

  private List<Long> fetchUpcomingTimeWindow(LocalDateTime firstWindowStartTime, LocalDateTime firstWindowEndTime,
      Long lastWindowEndTimeEpoch, RecurrenceType recurrenceType, TimeZone timeZone, RecurrenceSpec recurrenceSpec) {
    int recurrenceNumber = 0;
    int cnt = 1;
    int valueMultiplier = 1;
    if (recurrenceSpec != null) {
      if (recurrenceSpec.getValue() != null) {
        valueMultiplier = recurrenceSpec.getValue();
      }
    }
    List<Long> upcomingWindows = new ArrayList<>();
    Long currentWindowStartTime =
        getEpochValue(recurrenceType, firstWindowStartTime, timeZone, valueMultiplier * recurrenceNumber);
    while (currentWindowStartTime < lastWindowEndTimeEpoch) {
      if (getCurrentTime() < currentWindowStartTime) {
        upcomingWindows.add(currentWindowStartTime);
        cnt++;
        if (cnt > 10) {
          break;
        }
      }
      recurrenceNumber++;
      currentWindowStartTime =
          getEpochValue(recurrenceType, firstWindowStartTime, timeZone, valueMultiplier * recurrenceNumber);
    }
    return upcomingWindows;
  }

  private boolean currentWindowIsActive(Long windowStartTime, Long windowEndTime) {
    Long currentTime = getCurrentTime();
    return currentTime > windowStartTime && currentTime < windowEndTime;
  }

  public boolean currentWindowIsActive(CurrentOrUpcomingWindow currentOrUpcomingWindow) {
    return currentOrUpcomingWindow != null
        && currentWindowIsActive(currentOrUpcomingWindow.getStartTime(), currentOrUpcomingWindow.getEndTime());
  }

  public boolean globalFreezeIsActive(FreezeWindow freezeWindow) {
    TimeZone timeZone = TimeZone.getTimeZone(freezeWindow.getTimeZone());
    LocalDateTime startTime = LocalDateTime.parse(freezeWindow.getStartTime(), dtf);
    LocalDateTime endTime = getLocalDateTime(freezeWindow, timeZone, startTime);
    Long startTs = getEpochValue(null, startTime, timeZone, 0);
    Long endTs = getEpochValue(null, endTime, timeZone, 0);
    return currentWindowIsActive(startTs, endTs);
  }

  public Pair<LocalDateTime, LocalDateTime> setCurrWindowStartAndEndTime(LocalDateTime firstWindowStartTime,
      LocalDateTime firstWindowEndTime, RecurrenceType recurrenceType, TimeZone timeZone) {
    Long startTime = getEpochValue(recurrenceType, firstWindowStartTime, timeZone, 0);
    Long endTime = getEpochValue(recurrenceType, firstWindowEndTime, timeZone, 0);
    int recurrenceNumber = 0;
    Long currTime = getCurrentTime();
    while (currTime >= startTime) {
      if (currTime <= endTime) {
        LocalDateTime currWindowStartTime =
            Instant.ofEpochMilli(startTime).atZone(timeZone.toZoneId()).toLocalDateTime();
        LocalDateTime currWindowEndTime = Instant.ofEpochMilli(endTime).atZone(timeZone.toZoneId()).toLocalDateTime();
        return Pair.of(currWindowStartTime, currWindowEndTime);
      }
      recurrenceNumber++;
      startTime = getEpochValue(recurrenceType, firstWindowStartTime, timeZone, recurrenceNumber);
      endTime = getEpochValue(recurrenceType, firstWindowEndTime, timeZone, recurrenceNumber);
    }
    return Pair.of(firstWindowStartTime, firstWindowEndTime);
  }

  public Long getEpochValue(RecurrenceType offsetType, LocalDateTime date, TimeZone timeZone, int offsetValue) {
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

  public Long getEpochValueFromDateString(LocalDateTime date, TimeZone timeZone) {
    if (date == null) {
      return null;
    }

    ZoneOffset zoneOffset = timeZone.toZoneId().getRules().getOffset(now);
    return date.toInstant(zoneOffset).toEpochMilli();
  }

  public void validateTimeRange(FreezeWindow freezeWindow, FreezeStatus freezeStatus) throws ParseException {
    if (EmptyPredicate.isEmpty(freezeWindow.getTimeZone())) {
      throw new InvalidRequestException("Time zone cannot be empty");
    }
    TimeZone timeZone = TimeZone.getTimeZone(freezeWindow.getTimeZone());
    validateTimeZone(freezeWindow.getTimeZone(), timeZone);
    LocalDateTime firstWindowStartTime = LocalDateTime.parse(freezeWindow.getStartTime(), dtf);
    LocalDateTime firstWindowEndTime = getLocalDateTime(freezeWindow, timeZone, firstWindowStartTime);

    long timeDifferenceFromStartTime =
        FreezeTimeUtils.getEpochValueFromDateString(firstWindowStartTime, timeZone) - getCurrentTime();
    if (timeDifferenceFromStartTime > MAX_FREEZE_START_TIME) {
      throw new InvalidRequestException("Freeze window start time should be less than 5 years");
    }

    // Time difference in milliseconds.
    long timeDifferenceInMilliseconds = FreezeTimeUtils.getEpochValueFromDateString(firstWindowEndTime, timeZone)
        - FreezeTimeUtils.getEpochValueFromDateString(firstWindowStartTime, timeZone);
    if (timeDifferenceInMilliseconds < 0) {
      throw new InvalidRequestException("Window Start time is greater than Window end Time");
    }
    if (timeDifferenceInMilliseconds < MIN_FREEZE_WINDOW_TIME) {
      throw new InvalidRequestException("Freeze window time should be at least 30 minutes");
    }
    if (timeDifferenceInMilliseconds > MAX_FREEZE_WINDOW_TIME) {
      throw new InvalidRequestException("Freeze window time should be less than 365 days");
    }
    if (freezeWindow.getRecurrence() != null) {
      Recurrence recurrence = freezeWindow.getRecurrence();
      if (recurrence.getRecurrenceType() == null) {
        throw new InvalidRequestException("Recurrence Type cannot be empty");
      }
      if (recurrence.getSpec() != null) {
        if (recurrence.getSpec().getUntil() != null) {
          LocalDateTime until = LocalDateTime.parse(freezeWindow.getRecurrence().getSpec().getUntil(), dtf);
          Long untilMs = getEpochValue(recurrence.getRecurrenceType(), until, timeZone, 0);
          if (untilMs < getCurrentTime() && FreezeStatus.ENABLED.equals(freezeStatus)) {
            throw new InvalidRequestException("End time for recurrence cannot be less than current time");
          }
        }
        if (recurrence.getSpec().getValue() != null) {
          if (!RecurrenceType.MONTHLY.equals(recurrence.getRecurrenceType())) {
            throw new InvalidRequestException("Value is only supported for Monthly recurrence type");
          } else {
            if (recurrence.getSpec().getValue() < 2 || recurrence.getSpec().getValue() > 11) {
              throw new InvalidRequestException("Value can range only between 2 to 11");
            }
          }
        }
      }
    } else {
      Long endTime = getEpochValue(null, firstWindowEndTime, timeZone, 0);
      if (endTime < getCurrentTime() && FreezeStatus.ENABLED.equals(freezeStatus)) {
        throw new InvalidRequestException("Freeze Window is already expired");
      }
    }
  }

  private void validateTimeZone(String timeZoneId, TimeZone timeZone) {
    if (timeZone.getID().equals("GMT") && !timeZoneId.equals("GMT")) {
      throw new InvalidRequestException("Invalid TimeZone Selected");
    }
  }
}
