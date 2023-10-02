/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService;

import java.time.Duration;

public enum DurationDTO {
  FOUR_HOURS(Duration.ofHours(4)),
  TWENTY_FOUR_HOURS(Duration.ofDays(1)),
  THREE_DAYS(Duration.ofDays(3)),
  SEVEN_DAYS(Duration.ofDays(7)),
  THIRTY_DAYS(Duration.ofDays(30));

  private Duration duration;

  DurationDTO(Duration duration) {
    this.duration = duration;
  }
  public Duration getDuration() {
    return duration;
  }

  public static DurationDTO findClosestGreaterDurationDTO(Duration targetDuration) {
    DurationDTO bestFit = null;
    Duration smallestDifference = null;

    for (DurationDTO dto : DurationDTO.values()) {
      Duration difference = dto.getDuration().minus(targetDuration);

      if (difference.compareTo(Duration.ZERO) >= 0
          && (smallestDifference == null || difference.compareTo(smallestDifference) < 0)) {
        smallestDifference = difference;
        bestFit = dto;
      }
    }

    return bestFit;
  }
}
