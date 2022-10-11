/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import static java.time.temporal.ChronoUnit.DAYS;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import lombok.Builder;
import lombok.Value;

@Value
public class TimePeriod {
  LocalDateTime startTime;
  LocalDateTime endTime;
  @Builder
  public TimePeriod(LocalDate startDate, LocalDate endDate) {
    this(startDate.atStartOfDay(), endDate.atStartOfDay());
  }
  public static io.harness.cvng.servicelevelobjective.entities.TimePeriod createWithLocalTime(
      LocalDateTime startTime, LocalDateTime endTime) {
    return new io.harness.cvng.servicelevelobjective.entities.TimePeriod(startTime, endTime);
  }

  private TimePeriod(LocalDateTime startTime, LocalDateTime endTime) {
    this.startTime = startTime;
    this.endTime = endTime;
  }

  public int getRemainingDays(LocalDateTime currentDateTime) {
    return (int) ChronoUnit.DAYS.between(currentDateTime.toLocalDate(), endTime.toLocalDate());
  }
  public int getTotalDays() {
    return (int) DAYS.between(getStartTime(), getEndTime());
  }
  public int totalMinutes() {
    return (int) Duration.between(getStartTime(), getEndTime()).toMinutes();
  }

  /**
   * Start time is inclusive.
   */
  public Instant getStartTime(ZoneOffset zoneId) {
    return getStartTime().toInstant(zoneId);
  }

  /**
   * End time is exclusive.
   */
  public Instant getEndTime(ZoneOffset zoneId) {
    return getEndTime().toInstant(zoneId);
  }
}
