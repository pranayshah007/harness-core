/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.execution.ExecutionSummaryModuleInfoProvider;
import io.harness.pms.sdk.execution.beans.PipelineModuleInfo;
import io.harness.pms.sdk.execution.beans.StageModuleInfo;
import io.harness.steps.approval.stage.ApprovalStageStep;

import java.util.Objects;
import lombok.Builder;
import lombok.Data;

public class PmsExecutionServiceInfoProvider implements ExecutionSummaryModuleInfoProvider {
  @Override
  public PipelineModuleInfo getPipelineLevelModuleInfo(OrchestrationEvent event) {
    Level level = AmbianceUtils.obtainCurrentLevel(event.getAmbiance());
    return PmsPipelineModuleInfo.builder().hasApprovalStage(true).approvalStageName(level.getIdentifier()).build();
  }

  @Override
  public StageModuleInfo getStageLevelModuleInfo(OrchestrationEvent event) {
    return PmsNoopModuleInfo.builder().build();
  }

  @Override
  public boolean shouldRun(OrchestrationEvent event) {
    return Objects.equals(AmbianceUtils.getCurrentStepType(event.getAmbiance()), ApprovalStageStep.STEP_TYPE);
  }

  @Data
  @Builder
  public static class PmsNoopModuleInfo implements StageModuleInfo {}
}
