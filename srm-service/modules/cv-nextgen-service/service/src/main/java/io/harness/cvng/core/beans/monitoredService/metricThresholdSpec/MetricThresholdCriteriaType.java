/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.metricThresholdSpec;

import io.harness.cvng.beans.TimeSeriesThresholdComparisonType;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum MetricThresholdCriteriaType {
  @JsonProperty("Absolute") ABSOLUTE,
  @JsonProperty("Percentage") PERCENTAGE;

  public TimeSeriesThresholdComparisonType getTimeSeriesThresholdComparisonType() {
    switch (this) {
      case ABSOLUTE:
        return TimeSeriesThresholdComparisonType.ABSOLUTE;
      case PERCENTAGE:
        return TimeSeriesThresholdComparisonType.RATIO;
      default:
        throw new IllegalStateException("Unhanded MetricThresholdCriteriaType " + this);
    }
  }

  public static MetricThresholdCriteriaType getTimeSeriesThresholdComparisonType(
      TimeSeriesThresholdComparisonType timeSeriesThresholdComparisonType) {
    switch (timeSeriesThresholdComparisonType) {
      case ABSOLUTE:
        return MetricThresholdCriteriaType.ABSOLUTE;
      case RATIO:
        return MetricThresholdCriteriaType.PERCENTAGE;
      default:
        throw new IllegalStateException(
            "Unhanded TimeSeriesThresholdComparisonType " + timeSeriesThresholdComparisonType);
    }
  }

  public Double getRatio(Double value) {
    switch (this) {
      case ABSOLUTE:
        return value;
      case PERCENTAGE:
        return value * 0.01;
      default:
        throw new IllegalStateException("Unhanded MetricThresholdCriteriaType " + this);
    }
  }

  public Double getPercentage(Double value) {
    switch (this) {
      case ABSOLUTE:
        return value;
      case PERCENTAGE:
        return value * 100;
      default:
        throw new IllegalStateException("Unhanded MetricThresholdCriteriaType " + this);
    }
  }
}
