/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.beans;

import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.ng.beans.PageResponse;

import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class AnalyzedRadarChartLogDataWithCountDTO {
  private Integer totalClusters;
  @Singular private List<AnalyzedRadarChartLogDataWithCountDTO.LiveMonitoringEventCount> eventCounts;
  private PageResponse<AnalyzedRadarChartLogDataDTO> logAnalysisRadarCharts;

  @Value
  @Builder
  public static class LiveMonitoringEventCount {
    LogAnalysisResult.RadarChartTag clusterType;
    Integer count;

    public String getDisplayName() {
      return clusterType.getDisplayName();
    }
  }
}
