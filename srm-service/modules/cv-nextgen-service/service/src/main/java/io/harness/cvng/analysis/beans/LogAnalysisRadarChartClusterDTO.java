/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LogAnalysisRadarChartClusterDTO {
  private int label;
  String clusterId;
  String message;
  Risk risk;
  Double radius;
  Double angle;
  LogAnalysisRadarChartClusterDTO baseline;
  DeploymentLogAnalysisDTO.ClusterType clusterType;

  @JsonProperty(value = "hasControlData")
  public boolean hasControlData() {
    if (baseline != null) {
      return true;
    }
    return false;
  }

  public static LogAnalysisRadarChartClusterDTO buildWithLogAnalysisRadarChartListDTO(
      LogAnalysisRadarChartListDTO logAnalysisRadarChartListDTO) {
    LogAnalysisRadarChartClusterDTOBuilder logAnalysisRadarChartClusterDTOBuilder =
        LogAnalysisRadarChartClusterDTO.builder()
            .clusterId(logAnalysisRadarChartListDTO.getClusterId())
            .message(logAnalysisRadarChartListDTO.getMessage())
            .clusterType(logAnalysisRadarChartListDTO.getClusterType())
            .angle(logAnalysisRadarChartListDTO.getAngle())
            .label(logAnalysisRadarChartListDTO.getLabel())
            .radius(logAnalysisRadarChartListDTO.getRadius())
            .risk(logAnalysisRadarChartListDTO.getRisk());
    if (logAnalysisRadarChartListDTO.hasControlData()) {
      logAnalysisRadarChartClusterDTOBuilder.baseline(
          LogAnalysisRadarChartClusterDTO.builder()
              .message(logAnalysisRadarChartListDTO.getBaseline().getMessage())
              .clusterType(logAnalysisRadarChartListDTO.getBaseline().getClusterType())
              .angle(logAnalysisRadarChartListDTO.getBaseline().getAngle())
              .label(logAnalysisRadarChartListDTO.getBaseline().getLabel())
              .radius(logAnalysisRadarChartListDTO.getBaseline().getRadius())
              .risk(logAnalysisRadarChartListDTO.getBaseline().getRisk())
              .build());
    }
    return logAnalysisRadarChartClusterDTOBuilder.build();
  }
}
