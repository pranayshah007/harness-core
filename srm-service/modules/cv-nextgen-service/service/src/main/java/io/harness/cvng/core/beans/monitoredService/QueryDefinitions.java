/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService;

import io.harness.cvng.core.beans.RiskProfile;

import java.util.ArrayList;
import java.util.List;

public class QueryDefinitions {
  String identifier;
  String name;
  String groupName;
  QueryParams queryParams;
  boolean liveMonitoring;
  boolean continuousVerification;
  boolean sli;
  String query;
  List<TimeSeriesMetricPackDTO.MetricThreshold> metricThresholds = new ArrayList<>();
  RiskProfile riskProfile;
}