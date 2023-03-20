/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.advisers.prb;

import io.harness.advisers.CommonAdviserTypes;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.NextStepAdvise;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.KryoSerializer;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import javax.validation.constraints.NotNull;

// duplicate of RollbackCustomAdvisor
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineRollbackStartAdvisor implements Adviser {
  @Inject private KryoSerializer kryoSerializer;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  public static final AdviserType ADVISER_TYPE =
      AdviserType.newBuilder().setType(CommonAdviserTypes.PIPELINE_ROLLBACK_START.name()).build();

  @Override
  public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
    PipelineRollbackStartParameters parameters = extractParameters(advisingEvent);
    return AdviserResponse.newBuilder()
        .setType(AdviseType.NEXT_STEP)
        .setNextStepAdvise(NextStepAdvise.newBuilder().setNextNodeId(parameters.getPipelineRollbackStageId()).build())
        .build();
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    if (isRollbackMode(advisingEvent.getAmbiance().getMetadata().getExecutionMode())) {
      return false;
    }
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputService.resolveOptional(advisingEvent.getAmbiance(),
            RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.USE_PIPELINE_ROLLBACK_STRATEGY));
    if (!optionalSweepingOutput.isFound()) {
      return false;
    }
    OnFailPipelineRollbackOutput output = (OnFailPipelineRollbackOutput) optionalSweepingOutput.getOutput();
    return output.isShouldStartPipelineRollback();
  }

  @NotNull
  private PipelineRollbackStartParameters extractParameters(AdvisingEvent advisingEvent) {
    return (PipelineRollbackStartParameters) Preconditions.checkNotNull(
        kryoSerializer.asObject(advisingEvent.getAdviserParameters()));
  }

  boolean isRollbackMode(ExecutionMode executionMode) {
    return executionMode == ExecutionMode.POST_EXECUTION_ROLLBACK || executionMode == ExecutionMode.PIPELINE_ROLLBACK;
  }
}
