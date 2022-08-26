/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.metricThresholdSpec;

import io.harness.cvng.beans.TimeSeriesCustomThresholdActions;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum MetricCustomThresholdActions {
  @JsonProperty("Ignore") IGNORE,
  @JsonProperty("FailImmediately") FAIL_IMMEDIATELY,
  @JsonProperty("FailAfterOccurrence") FAIL_AFTER_OCCURRENCE,
  @JsonProperty("FailAfterConsecutiveOccurrence") FAIL_AFTER_CONSECUTIVE_OCCURRENCE;

  public TimeSeriesCustomThresholdActions getTimeSeriesCustomThresholdActions() {
    switch (this) {
      case IGNORE:
        return TimeSeriesCustomThresholdActions.IGNORE;
      case FAIL_IMMEDIATELY:
        return TimeSeriesCustomThresholdActions.FAIL_IMMEDIATELY;
      case FAIL_AFTER_OCCURRENCE:
        return TimeSeriesCustomThresholdActions.FAIL_AFTER_OCCURRENCES;
      case FAIL_AFTER_CONSECUTIVE_OCCURRENCE:
        return TimeSeriesCustomThresholdActions.FAIL_AFTER_CONSECUTIVE_OCCURRENCES;
      default:
        throw new IllegalStateException("Unhanded TimeSeriesCustomThresholdActions " + this);
    }
  }

  public static MetricCustomThresholdActions getMetricCustomThresholdActions(TimeSeriesCustomThresholdActions action) {
    switch (action) {
      case IGNORE:
        return MetricCustomThresholdActions.IGNORE;
      case FAIL_IMMEDIATELY:
        return MetricCustomThresholdActions.FAIL_IMMEDIATELY;
      case FAIL_AFTER_CONSECUTIVE_OCCURRENCES:
        return MetricCustomThresholdActions.FAIL_AFTER_CONSECUTIVE_OCCURRENCE;
      case FAIL_AFTER_OCCURRENCES:
        return MetricCustomThresholdActions.FAIL_AFTER_OCCURRENCE;
      default:
        throw new IllegalStateException("Unhanded TimeSeriesCustomThresholdActions " + action);
    }
  }
}
