/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.advisers;

import io.harness.advisers.rollback.OnFailRollbackOutput;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.NextStepAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.SectionStepSweepingOutput;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;

@OwnedBy(HarnessTeam.PIPELINE)
public class RollbackCustomAdviser implements Adviser {
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  public static final AdviserType ADVISER_TYPE =
      AdviserType.newBuilder().setType(CDAdviserTypes.ROLLBACK_CUSTOM.name()).build();

  @Inject private KryoSerializer kryoSerializer;

  @Override
  public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
    OnFailRollbackOutput rollbackOutcome = getRollbackOutputV2(advisingEvent);
    if (rollbackOutcome == null) {
      return null;
    }
    String nextNodeId = rollbackOutcome.getNextNodeId();
    NextStepAdvise.Builder builder = NextStepAdvise.newBuilder();
    if (EmptyPredicate.isNotEmpty(nextNodeId)) {
      builder.setNextNodeId(nextNodeId);
    }
    return AdviserResponse.newBuilder().setNextStepAdvise(builder.build()).setType(AdviseType.NEXT_STEP).build();
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    OnFailRollbackOutput rollbackOutcome = getRollbackOutputV2(advisingEvent);
    if (rollbackOutcome == null) {
      return false;
    }
    return StatusUtils.brokeStatuses().contains(advisingEvent.getToStatus());
  }

  /**
   * This checks if for a given node, there are any failed children. If yes, then check if any of the children published
   * rollbackOutcome to check if rollback is required or not.
   * See OnFailAdviser to get an idea of how rollbackOutcome is published.
   *
   * @param advisingEvent
   * @return
   */
  private OnFailRollbackOutput getRollbackOutputV2(AdvisingEvent advisingEvent) {
    OptionalSweepingOutput failedNodeSweepingOutput =
        executionSweepingOutputService.resolveOptional(advisingEvent.getAmbiance(),
            RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.FAILED_CHILDREN_OUTPUT));
    if (!failedNodeSweepingOutput.isFound()) {
      return getRollbackOutput(advisingEvent);
    }
    SectionStepSweepingOutput sectionStepSweepingOutput =
        (SectionStepSweepingOutput) failedNodeSweepingOutput.getOutput();
    return getOnFailRollbackOutput(advisingEvent.getAmbiance(), sectionStepSweepingOutput.getFailedNodeIds());
  }

  public OnFailRollbackOutput getOnFailRollbackOutput(Ambiance ambiance, List<String> failedNodeIds) {
    List<OptionalSweepingOutput> onFailRollbackOptionalOutput =
        executionSweepingOutputService.listOutputsWithGivenNameAndRuntimeIds(
            ambiance, YAMLFieldNameConstants.USE_ROLLBACK_STRATEGY, failedNodeIds);
    Optional<OptionalSweepingOutput> optionalOnFailOutput =
        onFailRollbackOptionalOutput.stream().filter(OptionalSweepingOutput::isFound).findFirst();
    return optionalOnFailOutput
        .map(optionalSweepingOutput -> (OnFailRollbackOutput) (optionalSweepingOutput.getOutput()))
        .orElse(null);
  }

  private OnFailRollbackOutput getRollbackOutput(AdvisingEvent advisingEvent) {
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputService.resolveOptional(advisingEvent.getAmbiance(),
            RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.USE_ROLLBACK_STRATEGY));
    if (!optionalSweepingOutput.isFound()) {
      return null;
    }
    return (OnFailRollbackOutput) optionalSweepingOutput.getOutput();
  }
}
