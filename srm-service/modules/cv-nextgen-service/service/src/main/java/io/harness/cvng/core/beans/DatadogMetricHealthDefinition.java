/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
public class DatadogMetricHealthDefinition extends HealthSourceMetricDefinition {
  String dashboardId;
  String dashboardName;
  String metricPath;
  String query;
  String groupingQuery;
  String metric;
  String aggregation;
  String serviceInstanceIdentifierTag;
  List<String> metricTags;
  boolean isManualQuery;
  boolean isCustomCreatedMetric;

  @JsonProperty(value = "isManualQuery")
  public boolean isManualQuery() {
    return isManualQuery;
  }
  @JsonProperty(value = "isCustomCreatedMetric")
  public boolean isCustomCreatedMetric() {
    return isCustomCreatedMetric;
  }

  public String getServiceInstanceIdentifierTag() {
    if (Objects.nonNull(analysis) && Objects.nonNull(analysis.getDeploymentVerification())
        && Objects.nonNull(analysis.deploymentVerification.serviceInstanceFieldName)) {
      return analysis.deploymentVerification.serviceInstanceFieldName;
    }
    return serviceInstanceIdentifierTag;
  }
}
