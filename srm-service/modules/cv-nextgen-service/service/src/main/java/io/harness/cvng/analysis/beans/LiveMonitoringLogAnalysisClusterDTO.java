/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisTag;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LiveMonitoringLogAnalysisClusterDTO {
  String text;
  Risk risk;
  double x;
  double y;
  LogAnalysisTag tag;

  public static class LiveMonitoringLogAnalysisClusterDTOBuilder {
    public LiveMonitoringLogAnalysisClusterDTOBuilder tag(LogAnalysisTag logAnalysisTag) {
      this.tag = logAnalysisTag;
      if (LogAnalysisTag.getAnomalousTags().contains(logAnalysisTag)) {
        this.risk(Risk.UNHEALTHY);
      } else {
        this.risk(Risk.HEALTHY);
      }
      return this;
    }
  }
}
