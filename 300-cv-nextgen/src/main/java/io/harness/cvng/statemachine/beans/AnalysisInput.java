/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.beans;

import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.core.beans.TimeRange;

import java.time.Instant;
import java.util.List;
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
  private String verificationTaskId;
  private Set<String> controlHosts;
  private Set<String> testHosts;
  private LearningEngineTask.LearningEngineTaskType learningEngineTaskType;

  public TimeRange getTimeRange() {
    return TimeRange.builder().startTime(startTime).endTime(endTime).build();
  }
}
