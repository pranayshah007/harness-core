/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.transformer;

import io.harness.cvng.downtime.beans.DowntimeDuration;
import io.harness.cvng.downtime.beans.DowntimeSpec;
import io.harness.cvng.downtime.entities.Downtime.DowntimeDetails;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public interface DowntimeSpecDetailsTransformer<E extends DowntimeDetails, T extends DowntimeSpec> {
  E getDowntimeDetails(T spec);
  T getDowntimeSpec(E entity, String timeZone);
  DowntimeDuration getDowntimeDuration(E entity);

  boolean isPastDowntime(E entity);
  List<Pair<Long, Long>> getStartAndEndTimesForFutureInstances(T spec);

  default long getEndTime(long startTime, DowntimeDuration downtimeDuration) {
    switch (downtimeDuration.getDurationType()) {
      case MINUTES:
        return startTime + (Duration.ofMinutes(downtimeDuration.getDurationValue()).toSeconds());
      case HOURS:
        return startTime + (Duration.ofHours(downtimeDuration.getDurationValue()).toSeconds());
      case DAYS:
        return startTime + (Duration.ofDays(downtimeDuration.getDurationValue()).toSeconds());
      case WEEKS:
        return startTime + (Duration.ofDays(7L * downtimeDuration.getDurationValue()).toSeconds());
      default:
        throw new IllegalStateException("type: " + downtimeDuration.getDurationType() + " is not handled");
    }
  }

  default LocalDateTime getLocalEndTime(LocalDateTime startTime, DowntimeDuration downtimeDuration) {
    switch (downtimeDuration.getDurationType()) {
      case MINUTES:
        return startTime.plusMinutes(downtimeDuration.getDurationValue());
      case HOURS:
        return startTime.plusHours(downtimeDuration.getDurationValue());
      case DAYS:
        return startTime.plusDays(downtimeDuration.getDurationValue());
      case WEEKS:
        return startTime.plusWeeks(downtimeDuration.getDurationValue());
      default:
        throw new IllegalStateException("type: " + downtimeDuration.getDurationType() + " is not handled");
    }
  }
}
