/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PlanResourceUtility {
  private final long HR_IN_MS = 60 * 60 * 1000;
  private final long DAY_IN_MS = 24 * HR_IN_MS;

  public static boolean validateInTimeLimitForRetry(long createdAt) {
    long currentTime = System.currentTimeMillis();
    long timeGap = 30 * DAY_IN_MS;
    if (Math.abs(currentTime - createdAt) > timeGap) {
      return false;
    }
    return true;
  }
}