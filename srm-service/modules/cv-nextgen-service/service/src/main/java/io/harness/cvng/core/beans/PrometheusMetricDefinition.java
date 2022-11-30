/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrometheusMetricDefinition extends HealthSourceMetricDefinition {
  private String query;
  @JsonIgnore private String serviceIdentifier;
  @JsonIgnore private String envIdentifier;
  private boolean isManualQuery;
  private String groupName;

  public String getServiceInstanceFieldName() {
    if (Objects.nonNull(analysis) && Objects.nonNull(analysis.getDeploymentVerification())
        && Objects.nonNull(analysis.deploymentVerification.serviceInstanceFieldName)) {
      return analysis.deploymentVerification.serviceInstanceFieldName;
    }
    return serviceInstanceFieldName;
  }

  private String serviceInstanceFieldName;
  private String prometheusMetric;
  private List<PrometheusFilter> serviceFilter;
  private List<PrometheusFilter> envFilter;
  private List<PrometheusFilter> additionalFilters;
  private String aggregation;

  @JsonProperty(value = "isManualQuery")
  public boolean isManualQuery() {
    return isManualQuery;
  }

  @Data
  @Builder
  public static class PrometheusFilter {
    private String labelName;
    private String labelValue;

    @JsonIgnore
    public String getQueryFilterString() {
      return labelName + "=\"" + labelValue + "\"";
    }
  }
}
