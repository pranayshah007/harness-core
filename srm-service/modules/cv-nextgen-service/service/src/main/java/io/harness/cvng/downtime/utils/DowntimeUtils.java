/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.downtime.utils;

import io.harness.cvng.downtime.beans.DowntimeDuration;
import io.harness.cvng.downtime.beans.DowntimeDurationType;

import java.time.Duration;

public class DowntimeUtils {
  public static DowntimeDuration getDowntimeDurationFromSeconds(long durationInSec) {
    if (durationInSec < Duration.ofMinutes(60).toSeconds()) {
      return DowntimeDuration.builder()
          .durationType(DowntimeDurationType.MINUTES)
          .durationValue((int) (durationInSec / Duration.ofMinutes(1).toSeconds()))
          .build();
    } else if (durationInSec < Duration.ofHours(24).toSeconds()) {
      return DowntimeDuration.builder()
          .durationType(DowntimeDurationType.HOURS)
          .durationValue((int) (durationInSec / Duration.ofHours(1).toSeconds()))
          .build();
    } else if (durationInSec < Duration.ofDays(7).toSeconds()) {
      return DowntimeDuration.builder()
          .durationType(DowntimeDurationType.DAYS)
          .durationValue((int) (durationInSec / Duration.ofDays(1).toSeconds()))
          .build();
    } else {
      return DowntimeDuration.builder()
          .durationType(DowntimeDurationType.WEEKS)
          .durationValue((int) (durationInSec / Duration.ofDays(7).toSeconds()))
          .build();
    }
  }
}
