/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetricPackDTO {
  String uuid;
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  DataSourceType dataSourceType;
  String identifier;
  CVMonitoringCategory category;
  Set<MetricDefinitionDTO> metrics;
  List<TimeSeriesThresholdDTO> thresholds;

  public Set<MetricDefinitionDTO> getMetrics() {
    if (metrics == null) {
      return new HashSet<>();
    }
    return metrics;
  }

  @Value
  @Builder
  public static class MetricDefinitionDTO {
    String name;
    String metricIdentifier;
    TimeSeriesMetricType type;
    String path;
    String validationPath;
    String responseJsonPath;
    String validationResponseJsonPath;
    List<TimeSeriesThresholdDTO> thresholds;
    boolean included;
  }
}
