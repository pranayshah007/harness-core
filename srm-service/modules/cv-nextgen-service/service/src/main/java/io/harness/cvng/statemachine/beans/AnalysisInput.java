/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.beans;

import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.core.beans.TimeRange;

import java.time.Instant;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants(innerTypeName = "AnalysisInputKeys")
@Builder
public class AnalysisInput {
  private Instant startTime;
  private Instant endTime;

  // Used in DeploymentTimeSeriesAnalysisState
  private String verificationTaskId;
  // Used in DeploymentTimeSeriesAnalysisState
  private String verificationJobInstanceId;
  // Used in DeploymentTimeSeriesAnalysisState
  private Set<String> controlHosts;
  // Used in DeploymentTimeSeriesAnalysisState
  private Set<String> testHosts;
  // Used in DeploymentTimeSeriesAnalysisState
  private LearningEngineTaskType learningEngineTaskType;
  private String accountId;
  @Builder.Default private boolean isSLORestoreTask = false;

  public TimeRange getTimeRange() {
    return TimeRange.builder().startTime(startTime).endTime(endTime).build();
  }
}
