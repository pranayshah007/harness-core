/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class StackdriverDefinition extends HealthSourceMetricDefinition {
  private String dashboardName;
  private String dashboardPath;
  private Object jsonMetricDefinition;
  private String jsonMetricDefinitionString;
  private List<String> metricTags;
  private boolean isManualQuery;
  private String serviceInstanceField;
  @JsonProperty(value = "isManualQuery")
  public boolean isManualQuery() {
    return isManualQuery;
  }

  public String getServiceInstanceField() {
    if (Objects.nonNull(analysis) && Objects.nonNull(analysis.deploymentVerification)
        && Objects.nonNull(analysis.getDeploymentVerification().getServiceInstanceFieldName())) {
      return analysis.getDeploymentVerification().getServiceInstanceFieldName();
    }
    return serviceInstanceField;
  }
}
