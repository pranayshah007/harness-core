/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import static io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterType.clusterTypeRiskComparator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LogAnalysisRadarChartListDTO implements Comparable<LogAnalysisRadarChartListDTO> {
  String message;
  String clusterId;
  int label;
  Risk risk;
  @JsonIgnore Double radius;
  @JsonIgnore Double angle;
  DeploymentLogAnalysisDTO.ClusterType clusterType;
  int count;
  @Deprecated List<Double> frequencyData;
  List<DeploymentLogAnalysisDTO.HostFrequencyData> hostFrequencyData;
  LogAnalysisRadarChartListDTO baseline;
  List<DeploymentLogAnalysisDTO.TimestampFrequencyCount> averageFrequencyData;
  public List<DeploymentLogAnalysisDTO.TimestampFrequencyCount> getAverageFrequencyData() {
    return DeploymentLogAnalysisDTO.HostFrequencyData.generateAverageTimeFrequencyList(hostFrequencyData);
  }

  public List<Double> getFrequencyData() {
    if (CollectionUtils.isNotEmpty(hostFrequencyData)) {
      return DeploymentLogAnalysisDTO.HostFrequencyData.generateAverageFrequencyList(hostFrequencyData);
    }
    return frequencyData;
  }

  @JsonProperty(value = "hasControlData")
  public boolean hasControlData() {
    if (baseline != null) {
      return true;
    }
    return false;
  }

  @Override
  public int compareTo(@NotNull LogAnalysisRadarChartListDTO o) {
    int clusterTypeComparision = clusterTypeRiskComparator.compare(this.getClusterType(), o.getClusterType());
    if (clusterTypeComparision != 0) {
      return clusterTypeComparision;
    } else if (o.getRisk().equals(this.getRisk())) {
      return o.getMessage().compareTo(this.getMessage());
    } else if (o.getRisk().isGreaterThan(this.getRisk())) {
      return 1;
    } else {
      return -1;
    }
  }
}
