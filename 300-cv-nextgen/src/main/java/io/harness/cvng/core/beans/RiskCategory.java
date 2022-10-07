/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.core.beans;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.TimeSeriesMetricType;

import com.fasterxml.jackson.annotation.JsonProperty;
public enum RiskCategory {
  @JsonProperty("Errors") ERROR("Errors", TimeSeriesMetricType.ERROR, CVMonitoringCategory.ERRORS),
  @JsonProperty("Infrastructure")
  INFRASTRUCTURE("Infrastructure", TimeSeriesMetricType.INFRA, CVMonitoringCategory.INFRASTRUCTURE),
  @JsonProperty("Performance_Throughput")
  PERFORMANCE_THROUGHPUT("Performance/Throughput", TimeSeriesMetricType.THROUGHPUT, CVMonitoringCategory.PERFORMANCE),
  @JsonProperty("Performance_Other")
  PERFORMANCE_OTHER("Performance/Other", TimeSeriesMetricType.OTHER, CVMonitoringCategory.PERFORMANCE),
  @JsonProperty("Performance_ResponseTime")
  PERFORMANCE_RESPONSE_TIME(
      "Performance/Response Time", TimeSeriesMetricType.RESP_TIME, CVMonitoringCategory.PERFORMANCE);

  RiskCategory(
      String displayName, TimeSeriesMetricType timeSeriesMetricType, CVMonitoringCategory cvMonitoringCategory) {
    this.displayName = displayName;
    this.timeSeriesMetricType = timeSeriesMetricType;
    this.cvMonitoringCategory = cvMonitoringCategory;
  }

  private final String displayName;
  private final TimeSeriesMetricType timeSeriesMetricType;
  private final CVMonitoringCategory cvMonitoringCategory;

  public String getDisplayName() {
    return displayName;
  }

  public TimeSeriesMetricType getTimeSeriesMetricType() {
    return timeSeriesMetricType;
  }

  public CVMonitoringCategory getCvMonitoringCategory() {
    return cvMonitoringCategory;
  }
  public static RiskCategory fromMetricAndCategory(
      TimeSeriesMetricType timeSeriesMetricType, CVMonitoringCategory cvMonitoringCategory) {
    for (RiskCategory riskCategory : RiskCategory.values()) {
      if (riskCategory.getTimeSeriesMetricType().equals(timeSeriesMetricType)
          && riskCategory.getCvMonitoringCategory().equals(cvMonitoringCategory)) {
        return riskCategory;
      }
    }
    return null;
  }
}
