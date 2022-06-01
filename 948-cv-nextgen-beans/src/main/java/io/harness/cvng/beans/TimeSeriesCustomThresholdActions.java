/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TimeSeriesCustomThresholdActions {
  FAIL_IMMEDIATELY("fail-immediately"),
  FAIL_AFTER_OCCURRENCES("fail-after-multiple-occurrences"),
  FAIL_AFTER_CONSECUTIVE_OCCURRENCES("fail-after-consecutive-occurrences");

  private String displayName;

  TimeSeriesCustomThresholdActions(String displayName) {
    this.displayName = displayName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static TimeSeriesCustomThresholdActions fromDisplayName(String displayName) {
    for (TimeSeriesCustomThresholdActions timeSeriesCustomThresholdActions :
        TimeSeriesCustomThresholdActions.values()) {
      if (timeSeriesCustomThresholdActions.displayName.equals(displayName)) {
        return timeSeriesCustomThresholdActions;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + displayName);
  }

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }
}
