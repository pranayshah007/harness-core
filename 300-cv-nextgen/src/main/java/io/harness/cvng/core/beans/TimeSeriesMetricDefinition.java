/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans;

import io.harness.cvng.beans.ThresholdConfigType;
import io.harness.cvng.beans.TimeSeriesCustomThresholdActions;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.beans.TimeSeriesThresholdActionType;
import io.harness.cvng.beans.TimeSeriesThresholdComparisonType;
import io.harness.cvng.beans.TimeSeriesThresholdType;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeSeriesMetricDefinition {
  String metricName;
  String metricIdentifier;
  TimeSeriesMetricType metricType;
  @Default String metricGroupName = "*";
  TimeSeriesThresholdActionType actionType;
  TimeSeriesThresholdComparisonType comparisonType;
  TimeSeriesCustomThresholdActions action;
  Integer occurrenceCount;
  TimeSeriesThresholdType thresholdType;
  Double value;
  ThresholdConfigType thresholdConfigType;
}
