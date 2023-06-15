/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.beans;

import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.beans.TimeSeriesMetricType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
@Builder
public class TimeSeriesMetricDataDTO implements Comparable<TimeSeriesMetricDataDTO> {
  String projectIdentifier;
  String orgIdentifier;
  String environmentIdentifier;
  String serviceIdentifier;
  String monitoredServiceIdentifier;
  TimeSeriesMetricType metricType;
  @Deprecated DataSourceType dataSourceType;
  MonitoredServiceDataSourceType monitoredServiceDataSourceType;
  CVMonitoringCategory category;

  String groupName;
  String metricName;
  String deeplinkURL;

  @Builder.Default Integer totalRisk = 0;

  SortedSet<MetricData> metricDataList;

  @JsonIgnore
  public Integer getTotalRisk() {
    return totalRisk;
  }

  public void addMetricData(Double value, long timestamp, Double riskScore) {
    if (metricDataList == null) {
      metricDataList = new TreeSet<>();
    }

    totalRisk += getRiskScore(riskScore);

    metricDataList.add(
        MetricData.builder().timestamp(timestamp).value(value).risk(Risk.valueOf(riskScore.intValue())).build());
  }

  private Integer getRiskScore(Double riskScore) {
    // returning 0 in case analysis is not done
    if (riskScore < 0) {
      return 0;
    }
    // adding this +1 as to consider the healthy service (riskScore =0) to be considered in the sorting order
    return riskScore.intValue() + 1;
  }

  @Override
  public int compareTo(@NotNull TimeSeriesMetricDataDTO o) {
    if (totalRisk != o.getTotalRisk()) {
      return o.getTotalRisk().compareTo(totalRisk);
    }
    if (!groupName.equals(o.getGroupName())) {
      return groupName.compareTo(o.getGroupName());
    }
    return metricName.compareTo(o.getMetricName());
  }

  @Data
  @Builder
  public static class MetricData implements Comparable<MetricData> {
    private Long timestamp;
    private Double value;
    Risk risk;
    @Override
    public int compareTo(@NotNull MetricData o) {
      return timestamp.compareTo(o.timestamp);
    }
  }
}
