/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.advisers.nextstep;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.pms.contracts.execution.Status.ABORTED;

import io.harness.advisers.prb.OnFailPipelineRollbackOutput;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.NextStepAdvise;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.KryoSerializer;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

@OwnedBy(PIPELINE)
public class NextStageAdviser implements Adviser {
  @Inject KryoSerializer kryoSerializer;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  public static final AdviserType ADVISER_TYPE =
      AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STAGE.name()).build();
  @Override
  public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
    NextStepAdviserParameters nextStepAdviserParameters = (NextStepAdviserParameters) Preconditions.checkNotNull(
        kryoSerializer.asObject(advisingEvent.getAdviserParameters()));
    AdviserResponse goToNextStageAdvise =
        AdviserResponse.newBuilder()
            .setNextStepAdvise(NextStepAdvise.newBuilder()
                                   .setNextNodeId(emptyIfNull(nextStepAdviserParameters.getNextNodeId()))
                                   .build())
            .setType(AdviseType.NEXT_STEP)
            .build();
    if (isRollbackMode(advisingEvent)) {
      return goToNextStageAdvise;
    }
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputService.resolveOptional(advisingEvent.getAmbiance(),
            RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.USE_PIPELINE_ROLLBACK_STRATEGY));
    if (!optionalSweepingOutput.isFound()) {
      return goToNextStageAdvise;
    }
    OnFailPipelineRollbackOutput output = (OnFailPipelineRollbackOutput) optionalSweepingOutput.getOutput();
    if (output.isShouldStartPipelineRollback()) {
      return AdviserResponse.newBuilder()
          .setNextStepAdvise(
              NextStepAdvise.newBuilder().setNextNodeId(nextStepAdviserParameters.getPipelineRollbackStageId()).build())
          .setType(AdviseType.NEXT_STEP)
          .build();
    }
    return goToNextStageAdvise;
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    return advisingEvent.getToStatus() != ABORTED;
  }

  boolean isRollbackMode(AdvisingEvent advisingEvent) {
    ExecutionMode executionMode = advisingEvent.getAmbiance().getMetadata().getExecutionMode();
    return executionMode == ExecutionMode.POST_EXECUTION_ROLLBACK || executionMode == ExecutionMode.PIPELINE_ROLLBACK;
  }
}
