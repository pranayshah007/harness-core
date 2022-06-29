/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Possible types of thresholds that can be applied to a metric.
 * Created by mike@ on 6/19/17.
 */
public enum TimeSeriesThresholdComparisonType {
  /**
   * A threshold that is divided by the previous build's value to yield a ratio.
   */
  RATIO("ratio"),
  /**
   * A threshold that is subtracted from the previous build's value to yield a delta.
   */
  DELTA("delta"),
  /**
   * A threshold that represents an absolute value to compare against rather than something in the previous build.
   */
  ABSOLUTE("absolute-value");

  private String displayName;

  TimeSeriesThresholdComparisonType(String displayName) {
    this.displayName = displayName;
  }

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static TimeSeriesThresholdComparisonType fromDisplayName(String displayName) {
    for (TimeSeriesThresholdComparisonType timeSeriesThresholdComparisonType :
        TimeSeriesThresholdComparisonType.values()) {
      if (timeSeriesThresholdComparisonType.displayName.equals(displayName)) {
        return timeSeriesThresholdComparisonType;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + displayName);
  }
}
