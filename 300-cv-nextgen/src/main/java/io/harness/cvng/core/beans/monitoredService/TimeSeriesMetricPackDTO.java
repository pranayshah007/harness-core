/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService;

import static io.harness.cvng.CVConstants.METRIC_THRESHOLD_METRIC_TYPE;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.TimeSeriesThresholdCriteria;
import io.harness.cvng.beans.TimeSeriesThresholdType;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.FailMetricThresholdSpec;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricThresholdActionType;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricThresholdCriteriaType;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricThresholdSpec;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.api.MetricPackService;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
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

  @Data
  @SuperBuilder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MetricThreshold {
    String groupName;
    String metricName;
    String metricIdentifier;
    String metricType;
    @JsonProperty(METRIC_THRESHOLD_METRIC_TYPE) MetricThresholdActionType type;

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = METRIC_THRESHOLD_METRIC_TYPE,
        include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
    @Valid
    @NotNull
    MetricThresholdSpec spec;
    MetricThresholdCriteria criteria;

    @JsonIgnore
    public List<TimeSeriesThresholdCriteria> getTimeSeriesThresholdCriteria() {
      List<TimeSeriesThresholdCriteria> timeSeriesThresholdCriterias = new ArrayList<>();
      Integer count = null;
      if (MetricThresholdActionType.FAIL.equals(type)) {
        count = ((FailMetricThresholdSpec) spec).getSpec().getCount();
      }
      if (Objects.nonNull(criteria.getSpec().greaterThan)) {
        timeSeriesThresholdCriterias.add(TimeSeriesThresholdCriteria.builder()
                                             .type(criteria.getType().getTimeSeriesThresholdComparisonType())
                                             .action(spec.getAction().getTimeSeriesCustomThresholdActions())
                                             .occurrenceCount(count)
                                             .thresholdType(TimeSeriesThresholdType.ACT_WHEN_HIGHER)
                                             .value(criteria.getType().getRatio(criteria.getSpec().greaterThan))
                                             .build());
      }
      if (Objects.nonNull(criteria.getSpec().lessThan)) {
        timeSeriesThresholdCriterias.add(TimeSeriesThresholdCriteria.builder()
                                             .type(criteria.getType().getTimeSeriesThresholdComparisonType())
                                             .action(spec.getAction().getTimeSeriesCustomThresholdActions())
                                             .occurrenceCount(count)
                                             .thresholdType(TimeSeriesThresholdType.ACT_WHEN_LOWER)
                                             .value(criteria.getType().getRatio(criteria.getSpec().lessThan))
                                             .build());
      }
      return timeSeriesThresholdCriterias;
    }

    @Data
    @SuperBuilder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetricThresholdCriteria {
      MetricThresholdCriteriaType type;
      MetricThresholdCriteriaSpec spec;

      @Data
      @SuperBuilder
      @FieldDefaults(level = AccessLevel.PRIVATE)
      @NoArgsConstructor
      @JsonIgnoreProperties(ignoreUnknown = true)
      public static class MetricThresholdCriteriaSpec {
        Double lessThan;
        Double greaterThan;
      }
    }
  }
}
