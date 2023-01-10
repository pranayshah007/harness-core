/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.healthsource;

import io.harness.beans.WithIdentifier;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.MetricThreshold;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryDefinition implements WithIdentifier {
  String identifier;
  String name;
  String groupName;
  @Builder.Default QueryParamsDTO queryParams = QueryParamsDTO.builder().build();
  Boolean liveMonitoringEnabled;
  Boolean continuousVerificationEnabled;
  Boolean sliEnabled;
  String query;
  @Builder.Default List<MetricThreshold> metricThresholds = new ArrayList<>();
  RiskProfile riskProfile;
}