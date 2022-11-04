/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;

import software.wings.sm.ContextElement;
import software.wings.sm.StateExecutionData;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class SkipStateExecutionData extends StateExecutionData {
  private String skipAssertionExpression;
  private String workflowId;

  @Getter @Setter private Integer pipelineStageParallelIndex;
  @Getter @Setter private String pipelineStageElementId;

  @Builder
  public SkipStateExecutionData(String stateName, String stateType, Long startTs, Long endTs, ExecutionStatus status,
      String errorMsg, Integer waitInterval, ContextElement element, Map<String, Object> stateParams,
      Map<String, Object> templateVariable, String skipAssertionExpression, String workflowId,
      Integer pipelineStageParallelIndex, String pipelineStageElementId) {
    super(stateName, stateType, startTs, endTs, status, errorMsg, waitInterval, element, stateParams, templateVariable);
    this.skipAssertionExpression = skipAssertionExpression;
    this.workflowId = workflowId;
    this.pipelineStageParallelIndex = pipelineStageParallelIndex;
    this.pipelineStageElementId = pipelineStageElementId;
  }

  public String getSkipAssertionExpression() {
    return skipAssertionExpression;
  }

  public void setSkipAssertionExpression(String skipAssertionExpression) {
    this.skipAssertionExpression = skipAssertionExpression;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
  }

  @Override
  public Map<String, ExecutionDataValue> getExecutionSummary() {
    Map<String, ExecutionDataValue> executionDetails = super.getExecutionSummary();
    putNotNull(executionDetails, "skipAssertionExpression",
        ExecutionDataValue.builder().displayName("Skip Condition").value(skipAssertionExpression).build());
    return executionDetails;
  }
}
