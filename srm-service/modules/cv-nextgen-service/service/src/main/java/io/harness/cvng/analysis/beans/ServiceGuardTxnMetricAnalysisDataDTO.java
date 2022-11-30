/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import io.harness.cvng.analysis.entities.TimeSeriesCumulativeSums.MetricSum;
import io.harness.cvng.beans.TimeSeriesMetricType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceGuardTxnMetricAnalysisDataDTO {
  private boolean longTermPattern;
  private long lastSeenTime;
  private int risk;
  private double score;
  private boolean isKeyTransaction;
  private List<Double> shortTermHistory;
  private List<TimeSeriesAnomaliesDTO> anomalousPatterns;
  private MetricSumDTO cumulativeSums;
  private TimeSeriesMetricType metricType;
  public Risk getRisk() {
    return Risk.valueOf(risk);
  }

  @Data
  @Builder
  @FieldNameConstants(innerTypeName = "MetricSumsDTOKeys")
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MetricSumDTO {
    // in LE we pass metric identifier as the metric_name, as metric_name is the identifier for LE
    @JsonProperty("metricName") private String metricIdentifier;
    private double risk;
    private double data;
    public MetricSum toMetricSum() {
      return MetricSum.builder().metricIdentifier(metricIdentifier).risk(risk).data(data).build();
    }

    public static MetricSumDTO toMetricSumDTO(MetricSum metricSum) {
      return MetricSumDTO.builder()
          .metricIdentifier(metricSum.getMetricIdentifier())
          .risk(metricSum.getRisk())
          .data(metricSum.getData())
          .build();
    }
  }
}
