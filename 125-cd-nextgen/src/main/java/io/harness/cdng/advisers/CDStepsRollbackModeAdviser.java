/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.advisers;

import static io.harness.pms.contracts.execution.Status.ABORTED;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.advisers.pipelinerollback.OnFailPipelineRollbackOutput;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.NextStepAdvise;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.KryoSerializer;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.List;

public class CDStepsRollbackModeAdviser implements Adviser {
  @Inject KryoSerializer kryoSerializer;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  public static final AdviserType ADVISER_TYPE =
      AdviserType.newBuilder().setType(CDAdviserTypes.CD_STEPS_ROLLBACK_MODE.name()).build();

  @Override
  public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
    NextStepAdviserParameters nextStepAdviserParameters = (NextStepAdviserParameters) Preconditions.checkNotNull(
        kryoSerializer.asObject(advisingEvent.getAdviserParameters()));
    return AdviserResponse.newBuilder()
        .setNextStepAdvise(NextStepAdvise.newBuilder().setNextNodeId(nextStepAdviserParameters.getNextNodeId()).build())
        .setType(AdviseType.NEXT_STEP)
        .build();
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    if (advisingEvent.getToStatus() == ABORTED) {
      return false;
    }
    // Todo: Create a custom adviser for CD and stitch it with the planNode for cd
    // This is required so that the next step does not run if the given stage is rolled back. If removed, the next step
    // would be marked as skipped, rather than NotStarted
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputService.resolveOptional(advisingEvent.getAmbiance(),
            RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.STOP_STEPS_SEQUENCE));
    if (optionalSweepingOutput.isFound()) {
      return false;
    }
    OptionalSweepingOutput optionalSweepingOutput1 =
        executionSweepingOutputService.resolveOptional(advisingEvent.getAmbiance(),
            RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.PIPELINE_ROLLBACK_FAILURE_INFO));
    if (!optionalSweepingOutput1.isFound()) {
      // sweeping output not found means that this stage was successful, but is being rolled back because of PRB, and
      // hence rollback steps should start only if the steps are under execution
      return isUnderExecution(advisingEvent.getAmbiance().getLevelsList());
    }
    OnFailPipelineRollbackOutput onFailPipelineRollbackOutput =
        (OnFailPipelineRollbackOutput) optionalSweepingOutput1.getOutput();

    return haveSameAncestor(
        onFailPipelineRollbackOutput.getLevelsAtFailurePoint(), advisingEvent.getAmbiance().getLevelsList());
  }

  private boolean haveSameAncestor(List<Level> levelsAtFailurePoint, List<Level> levelsInAmbiance) {
    return isUnderExecution(levelsAtFailurePoint) == isUnderExecution(levelsInAmbiance);
  }

  private boolean isUnderExecution(List<Level> levels) {
    for (Level level : levels) {
      if (level.getIdentifier().equals(NGCommonUtilPlanCreationConstants.EXECUTION_NODE_IDENTIFIER)) {
        return true;
      }
    }
    return false;
  }
}
