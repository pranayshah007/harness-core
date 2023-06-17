/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.advisers.nextstep;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.execution.Status.ABORTED;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.exception.InvalidRequestException;
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
import io.harness.utils.ExecutionModeUtils;

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
    String siblingStageNodeId = getSiblingStageNodeId(advisingEvent);
    AdviserResponse goToSiblingAdvise =
        AdviserResponse.newBuilder()
            .setNextStepAdvise(NextStepAdvise.newBuilder().setNextNodeId(siblingStageNodeId).build())
            .setType(AdviseType.NEXT_STEP)
            .build();
    if (isRollbackModeExecution(advisingEvent)) {
      // execution is already in rollback mode, hence no case of starting a pipeline rollback stage will come up
      return goToSiblingAdvise;
    }
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputService.resolveOptional(advisingEvent.getAmbiance(),
            RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.USE_PIPELINE_ROLLBACK_STRATEGY));
    if (!optionalSweepingOutput.isFound()) {
      return goToSiblingAdvise;
    }
    return AdviserResponse.newBuilder()
        .setNextStepAdvise(NextStepAdvise.newBuilder().setNextNodeId(getPipelineRollbackStageId(advisingEvent)).build())
        .setType(AdviseType.NEXT_STEP)
        .build();
  }

  String getSiblingStageNodeId(AdvisingEvent advisingEvent) {
    Object adviserParams = Preconditions.checkNotNull(kryoSerializer.asObject(advisingEvent.getAdviserParameters()));
    if (adviserParams instanceof NextStepAdviserParameters) {
      // todo: remove all usages of NextStepAdviserParameters from Plan Creators for Next Stage advisor.
      NextStepAdviserParameters nextStepAdviserParameters = (NextStepAdviserParameters) adviserParams;
      return HarnessStringUtils.emptyIfNull(nextStepAdviserParameters.getNextNodeId());
    } else if (adviserParams instanceof NextStageAdviserParameters) {
      NextStageAdviserParameters nextStageAdviserParameters = (NextStageAdviserParameters) adviserParams;
      return HarnessStringUtils.emptyIfNull(nextStageAdviserParameters.getNextNodeId());
    }
    throw new InvalidRequestException(
        "Unsupported class type for Adviser Params found in Next Stage Advisor: " + adviserParams.getClass().getName());
  }

  String getPipelineRollbackStageId(AdvisingEvent advisingEvent) {
    Object adviserParams = Preconditions.checkNotNull(kryoSerializer.asObject(advisingEvent.getAdviserParameters()));
    if (!(adviserParams instanceof NextStageAdviserParameters)) {
      throw new InvalidRequestException(
          "Unsupported class type for Adviser Params found in Next Stage Advisor when trying to initiate Pipeline Rollback: "
          + adviserParams.getClass().getName());
    }
    NextStageAdviserParameters nextStageAdviserParameters = (NextStageAdviserParameters) adviserParams;
    return HarnessStringUtils.emptyIfNull(nextStageAdviserParameters.getPipelineRollbackStageId());
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    return advisingEvent.getToStatus() != ABORTED;
  }

  static boolean isRollbackModeExecution(AdvisingEvent advisingEvent) {
    ExecutionMode executionMode = advisingEvent.getAmbiance().getMetadata().getExecutionMode();
    return ExecutionModeUtils.isRollbackMode(executionMode);
  }
}
