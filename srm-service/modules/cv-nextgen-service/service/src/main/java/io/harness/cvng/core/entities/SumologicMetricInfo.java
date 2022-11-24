/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder
@FieldNameConstants(innerTypeName = "SumologicMetricInfoKeys")
public class SumologicMetricInfo extends AnalysisInfo {
  String query;
  TimeSeriesMetricType metricType;
  // TODO finialize whats required here
  String serviceInstanceFieldName;
  MetricResponseMapping responseMapping;
}