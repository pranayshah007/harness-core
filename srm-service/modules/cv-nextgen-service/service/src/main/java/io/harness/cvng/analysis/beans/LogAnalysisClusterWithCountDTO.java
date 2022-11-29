/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO.ClusterType;
import io.harness.ng.beans.PageResponse;

import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class LogAnalysisClusterWithCountDTO {
  private Integer totalClusters;
  @Singular private List<EventCount> eventCounts;
  private PageResponse<LogAnalysisClusterDTO> logAnalysisClusterDTO;

  @Value
  @Builder
  public static class EventCount {
    ClusterType clusterType;
    Integer count;

    public String getDisplayName() {
      return clusterType.getDisplayName();
    }
  }
}
