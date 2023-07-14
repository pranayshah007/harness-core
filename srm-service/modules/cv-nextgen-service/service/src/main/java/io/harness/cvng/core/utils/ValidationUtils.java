/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils;

import io.harness.cvng.core.beans.monitoredService.DurationDTO;
import io.harness.exception.InvalidRequestException;

import java.time.Duration;
import java.time.Instant;

public class ValidationUtils {
  public static void validateOneOfDurationOrStartTimeIsPresent(DurationDTO durationDTO, Long startTime) {
    if (durationDTO == null && startTime == null) {
      throw new InvalidRequestException("One of duration or start time should be present.");
    }
  }

  public static void validationIfBothDurationAndStartTimeIsPresent(
      DurationDTO durationDTO, Long startTime, Long endTime) {
    if (durationDTO != null && startTime != null) {
      Duration duration = Duration.ofSeconds(endTime - startTime);
      if (durationDTO.getDuration().toSeconds() != duration.toSeconds()) {
        throw new InvalidRequestException(
            "Duration field value and duration from the start time and endTime is different. Make sure you pass either one of them or the duration is same from both.");
      }
    }
  }

  public static Instant validateTheDifferenceBetweenStartAndEndTimeAndGetStartTime(
      DurationDTO durationDTO, Long startTime, Long endTime, Duration minDurationDifference) {
    Instant startTimeInstant = getStartTimeInstant(durationDTO, startTime, endTime);
    Duration duration = Duration.ofSeconds(endTime - startTimeInstant.getEpochSecond());
    if (duration.toSeconds() < minDurationDifference.toSeconds()) {
      throw new InvalidRequestException("Start time and endTime should have at least 5 minutes difference");
    }
    return startTimeInstant;
  }

  private static Instant getStartTimeInstant(DurationDTO durationDTO, Long startTime, Long endTime) {
    Instant startTimeInstant;
    if (startTime == null) {
      startTimeInstant = Instant.ofEpochMilli(endTime).minus(durationDTO.getDuration());
    } else {
      startTimeInstant = Instant.ofEpochMilli(startTime);
    }
    return startTimeInstant;
  }
}
