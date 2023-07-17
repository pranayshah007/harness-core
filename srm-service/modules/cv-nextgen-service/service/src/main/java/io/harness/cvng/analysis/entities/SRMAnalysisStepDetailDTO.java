/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

import io.harness.cvng.beans.change.SRMAnalysisStatus;

import java.time.Duration;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SRMAnalysisStepDetailDTO {
  @NotNull private long analysisStartTime;
  @NotNull private long analysisEndTime;

  @NotNull private Duration analysisDuration;

  @NotNull private SRMAnalysisStatus analysisStatus;

  @NotNull private String monitoredServiceIdentifier;

  @NotNull private String executionDetailIdentifier;

  public static SRMAnalysisStepDetailDTO getDTOFromEntity(SRMAnalysisStepExecutionDetail stepExecutionDetail) {
    return SRMAnalysisStepDetailDTO.builder()
        .analysisStatus(stepExecutionDetail.getAnalysisStatus())
        .monitoredServiceIdentifier(stepExecutionDetail.getMonitoredServiceIdentifier())
        .analysisStartTime(stepExecutionDetail.getAnalysisStartTime())
        .analysisEndTime(stepExecutionDetail.getAnalysisEndTime())
        .analysisDuration(stepExecutionDetail.getAnalysisDuration())
        .executionDetailIdentifier(stepExecutionDetail.getUuid())
        .build();
  }
}
