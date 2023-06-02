/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.transformer;

import static java.lang.Math.max;

import io.harness.cvng.downtime.beans.DowntimeDuration;
import io.harness.cvng.downtime.beans.DowntimeRecurrence;
import io.harness.cvng.downtime.beans.RecurringDowntimeSpec;
import io.harness.cvng.downtime.entities.Downtime.RecurringDowntimeDetails;
import io.harness.cvng.downtime.utils.DateTimeUtils;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;

public class RecurringDowntimeSpecDetailsTransformer
    implements DowntimeSpecDetailsTransformer<RecurringDowntimeDetails, RecurringDowntimeSpec> {
  @Inject Clock clock;

  @Override
  public RecurringDowntimeDetails getDowntimeDetails(RecurringDowntimeSpec spec) {
    Optional<String> startDateTime = Optional.ofNullable(spec.getStartDateTime());
    Optional<String> recurrenceEndDateTime = Optional.ofNullable(spec.getRecurrenceEndDateTime());
    return RecurringDowntimeDetails.builder()
        .recurrenceEndTime(recurrenceEndDateTime.isPresent()
                ? DateTimeUtils.getEpochValueFromDateString(recurrenceEndDateTime.get(), spec.getTimezone())
                : spec.getRecurrenceEndTime())
        .downtimeDuration(spec.getDowntimeDuration())
        .downtimeRecurrence(spec.getDowntimeRecurrence())
        .startTime(startDateTime.isPresent()
                ? DateTimeUtils.getEpochValueFromDateString(startDateTime.get(), spec.getTimezone())
                : spec.getStartTime())
        .build();
  }

  @Override
  public RecurringDowntimeSpec getDowntimeSpec(RecurringDowntimeDetails entity, String timeZone) {
    return RecurringDowntimeSpec.builder()
        .startTime(entity.getStartTime())
        .recurrenceEndTime(entity.getRecurrenceEndTime())
        .recurrenceEndDateTime(DateTimeUtils.getDateStringFromEpoch(entity.getRecurrenceEndTime(), timeZone))
        .downtimeDuration(entity.getDowntimeDuration())
        .downtimeRecurrence(entity.getDowntimeRecurrence())
        .build();
  }

  @Override
  public DowntimeDuration getDowntimeDuration(RecurringDowntimeDetails entity) {
    return entity.getDowntimeDuration();
  }

  @Override
  public boolean isPastDowntime(RecurringDowntimeDetails entity) {
    return entity.getRecurrenceEndTime() < clock.millis() / 1000;
  }

  @Override
  public List<Pair<Long, Long>> getStartAndEndTimesForFutureInstances(RecurringDowntimeSpec spec) {
    List<Pair<Long, Long>> futureInstances = new ArrayList<>();
    if (spec.getStartDateTime() != null) {
      LocalDateTime currentStartDateTime = DateTimeUtils.getLocalDateFromDateString(spec.getStartDateTime());
      long currentStartTime = DateTimeUtils.getEpochValueFromLocalTime(currentStartDateTime, spec.getTimezone());
      long endTime = DateTimeUtils.getEpochValueFromDateString(spec.getRecurrenceEndDateTime(), spec.getTimezone());
      long currentTime = clock.millis() / 1000;
      for (; currentStartTime < endTime;) {
        LocalDateTime currentEndDateTime = getLocalEndTime(currentStartDateTime, spec.getDowntimeDuration());
        long currentEndTime = DateTimeUtils.getEpochValueFromLocalTime(currentEndDateTime, spec.getTimezone());
        if (currentEndTime <= endTime && currentEndTime >= currentTime) {
          futureInstances.add(Pair.of(max(currentStartTime, currentTime), currentEndTime));
        }
        currentStartDateTime = getLocalNextStartTime(currentStartDateTime, spec.getDowntimeRecurrence());
        currentStartTime = DateTimeUtils.getEpochValueFromLocalTime(currentStartDateTime, spec.getTimezone());
      }
    } else {
      long startTime = spec.getStartTime();
      long endTime = spec.getRecurrenceEndTime();
      long currentTime = clock.millis() / 1000;
      for (long currentStartTime = startTime; currentStartTime < endTime;) {
        long currentEndTime = getEndTime(currentStartTime, spec.getDowntimeDuration());
        if (currentEndTime <= endTime && currentEndTime >= currentTime) {
          futureInstances.add(Pair.of(max(currentStartTime, currentTime), currentEndTime));
        }
        currentStartTime = getNextStartTime(currentStartTime, spec.getDowntimeRecurrence());
      }
    }
    return futureInstances;
  }

  private long getNextStartTime(long currentStartTime, DowntimeRecurrence recurrence) {
    Calendar cal = Calendar.getInstance();
    switch (recurrence.getRecurrenceType()) {
      case DAY:
        return currentStartTime + Duration.ofDays(recurrence.getRecurrenceValue()).toSeconds();
      case WEEK:
        return currentStartTime + Duration.ofDays(7L * recurrence.getRecurrenceValue()).toSeconds();
      case MONTH:
        cal.setTimeInMillis(currentStartTime * 1000);
        cal.add(Calendar.MONTH, 1);
        return cal.getTimeInMillis() / 1000;
      case YEAR:
        cal.setTimeInMillis(currentStartTime * 1000);
        cal.add(Calendar.YEAR, 1);
        return cal.getTimeInMillis() / 1000;
      default:
        throw new IllegalStateException("type: " + recurrence.getRecurrenceType() + " is not handled");
    }
  }

  private LocalDateTime getLocalNextStartTime(LocalDateTime currentStartDateTime, DowntimeRecurrence recurrence) {
    switch (recurrence.getRecurrenceType()) {
      case DAY:
        return currentStartDateTime.plusDays(recurrence.getRecurrenceValue());
      case WEEK:
        return currentStartDateTime.plusWeeks(recurrence.getRecurrenceValue());
      case MONTH:
        return currentStartDateTime.plusMonths(recurrence.getRecurrenceValue());
      case YEAR:
        return currentStartDateTime.plusYears(recurrence.getRecurrenceValue());
      default:
        throw new IllegalStateException("type: " + recurrence.getRecurrenceType() + " is not handled");
    }
  }
}
