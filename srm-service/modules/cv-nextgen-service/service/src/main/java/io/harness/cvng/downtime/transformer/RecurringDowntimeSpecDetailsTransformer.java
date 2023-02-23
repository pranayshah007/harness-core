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

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class RecurringDowntimeSpecDetailsTransformer
    implements DowntimeSpecDetailsTransformer<RecurringDowntimeDetails, RecurringDowntimeSpec> {
  @Inject Clock clock;

  @Override
  public RecurringDowntimeDetails getDowntimeDetails(RecurringDowntimeSpec spec) {
    return RecurringDowntimeDetails.builder()
        .recurrenceEndTime(spec.getRecurrenceEndTime())
        .downtimeDuration(spec.getDowntimeDuration())
        .downtimeRecurrence(spec.getDowntimeRecurrence())
        .startTime(spec.getStartTime())
        .build();
  }

  @Override
  public RecurringDowntimeSpec getDowntimeSpec(RecurringDowntimeDetails entity) {
    return RecurringDowntimeSpec.builder()
        .startTime(entity.getStartTime())
        .recurrenceEndTime(entity.getRecurrenceEndTime())
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
    long startTime = spec.getStartTime();
    long endTime = spec.getRecurrenceEndTime();
    long currentTime = clock.millis() / 1000;
    List<Pair<Long, Long>> futureInstances = new ArrayList<>();
    for (long currentStartTime = startTime; currentStartTime < endTime;) {
      long currentEndTime = getEndTime(currentStartTime, spec.getDowntimeDuration());
      if (currentEndTime <= endTime && currentEndTime >= currentTime) {
        futureInstances.add(Pair.of(max(currentStartTime, currentTime), currentEndTime));
      }
      currentStartTime = getNextStartTime(currentStartTime, spec.getDowntimeRecurrence());
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
        return currentStartTime + cal.getTimeInMillis() / 1000;
      case YEAR:
        cal.setTimeInMillis(currentStartTime * 1000);
        cal.add(Calendar.YEAR, 1);
        return currentStartTime + cal.getTimeInMillis() / 1000;
      default:
        throw new IllegalStateException("type: " + recurrence.getRecurrenceType() + " is not handled");
    }
  }
}
