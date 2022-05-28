/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import io.harness.cvng.analysis.entities.TimeSeriesAnomalousPatterns.TimeSeriesAnomalies;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TimeSeriesAnomaliesDTO {
  private String transactionName;
  // in LE we pass metric identifier as the metric_name, as metric_name is the identifier for LE
  @JsonProperty("metricName") private String metricIdentifier;
  private List<Double> testData;
  private List<Long> anomalousTimestamps;

  public TimeSeriesAnomalies toTimeSeriesAnomalies() {
    return TimeSeriesAnomalies.builder()
        .metricIdentifier(metricIdentifier)
        .anomalousTimestamps(anomalousTimestamps)
        .testData(testData)
        .transactionName(transactionName)
        .build();
  }

  public static TimeSeriesAnomaliesDTO toTimeSeriesAnomaliesDTO(TimeSeriesAnomalies timeSeriesAnomalies) {
    return TimeSeriesAnomaliesDTO.builder()
        .metricIdentifier(timeSeriesAnomalies.getMetricIdentifier())
        .transactionName(timeSeriesAnomalies.getTransactionName())
        .anomalousTimestamps(timeSeriesAnomalies.getAnomalousTimestamps())
        .testData(timeSeriesAnomalies.getTestData())
        .build();
  }
}
