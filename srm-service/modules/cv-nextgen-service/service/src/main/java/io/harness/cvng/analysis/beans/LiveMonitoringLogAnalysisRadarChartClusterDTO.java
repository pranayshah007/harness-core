/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisTag;
import io.harness.cvng.analysis.entities.LogAnalysisResult.RadarChartTag;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveMonitoringLogAnalysisRadarChartClusterDTO {
  String message;
  String clusterId;
  Risk risk;
  Double angle;
  Double radius;
  RadarChartTag clusterType;

  public static class LiveMonitoringLogAnalysisRadarChartClusterDTOBuilder {
    public LiveMonitoringLogAnalysisRadarChartClusterDTOBuilder clusterType(RadarChartTag clusterType) {
      this.clusterType = clusterType;
      if (LogAnalysisTag.getAnomalousTags().contains(LogAnalysisResult.RadarChartTagToLogAnalysisTag(clusterType))) {
        this.risk(Risk.UNHEALTHY);
      } else {
        this.risk(Risk.HEALTHY);
      }
      return this;
    }
  }
}
