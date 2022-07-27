/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService;

import static io.harness.cvng.CVConstants.DATA_SOURCE_TYPE;
import static io.harness.cvng.CVConstants.METRIC_THRESHOLD_METRIC_TYPE;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricThresholdActionType;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricThresholdCriteriaType;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricThresholdSpec;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.api.MetricPackService;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesMetricPackDTO {
  @NotNull private String identifier;
  List<MetricThreshold> metricThresholds;

  public MetricPack toMetricPack(String accountId, String orgId, String projectId, DataSourceType dataSourceType,
      MetricPackService metricPackService) {
    return metricPackService.getMetricPack(accountId, orgId, projectId, dataSourceType, identifier);
  }

  public static TimeSeriesMetricPackDTO toMetricPackDTO(MetricPack metricPack) {
    return TimeSeriesMetricPackDTO.builder().identifier(metricPack.getIdentifier()).build();
  }

  @SuperBuilder
  @Data
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MetricThreshold {
    String groupName;
    String metricName;
    String metricIdentifier;
    @JsonProperty(DATA_SOURCE_TYPE) MetricThresholdActionType type;

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = METRIC_THRESHOLD_METRIC_TYPE,
        include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
    @Valid
    @NotNull
    MetricThresholdSpec spec;
    MetricThresholdCriteria criteria;

    @SuperBuilder
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetricThresholdCriteria {
      MetricThresholdCriteriaType type;
      MetricThresholdCriteriaSpec spec;

      @SuperBuilder
      @Data
      @NoArgsConstructor
      @JsonIgnoreProperties(ignoreUnknown = true)
      public static class MetricThresholdCriteriaSpec {
        Double lessThan;
        Double greaterThan;
      }
    }
  }
}
