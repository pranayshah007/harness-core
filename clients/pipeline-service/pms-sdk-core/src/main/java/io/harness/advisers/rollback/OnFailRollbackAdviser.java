/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.advisers.rollback;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.advisers.CommonAdviserTypes;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.NextStepAdvise;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.KryoSerializer;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class OnFailRollbackAdviser implements Adviser {
  public static final AdviserType ADVISER_TYPE =
      AdviserType.newBuilder().setType(CommonAdviserTypes.ON_FAIL_ROLLBACK.name()).build();
  @Inject private KryoSerializer kryoSerializer;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
    OnFailRollbackParameters onFailRollbackParameters = extractParameters(advisingEvent);
    String nextNodeId = onFailRollbackParameters.getStrategyToUuid().get(onFailRollbackParameters.getStrategy());
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputService.resolveOptional(advisingEvent.getAmbiance(),
            RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.USE_ROLLBACK_STRATEGY));
    if (!optionalSweepingOutput.isFound()) {
      try {
        executionSweepingOutputService.consume(advisingEvent.getAmbiance(),
            YAMLFieldNameConstants.USE_ROLLBACK_STRATEGY, OnFailRollbackOutput.builder().nextNodeId(nextNodeId).build(),
            null);
      } catch (Exception e) {
        log.warn("Ignoring duplicate sweeping output of - " + YAMLFieldNameConstants.USE_ROLLBACK_STRATEGY);
      }
    }

    optionalSweepingOutput = executionSweepingOutputService.resolveOptional(advisingEvent.getAmbiance(),
        RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.STOP_STEPS_SEQUENCE));
    if (!optionalSweepingOutput.isFound()) {
      try {
        executionSweepingOutputService.consume(advisingEvent.getAmbiance(), YAMLFieldNameConstants.STOP_STEPS_SEQUENCE,
            RollbackTriggeredOutput.builder().rollbackTriggered(true).build(), StepCategory.STAGE.name());
      } catch (Exception e) {
        log.warn("Ignoring duplicate sweeping output of - " + YAMLFieldNameConstants.STOP_STEPS_SEQUENCE);
      }
    }

    return AdviserResponse.newBuilder()
        .setNextStepAdvise(NextStepAdvise.newBuilder().build())
        .setType(AdviseType.NEXT_STEP)
        .build();
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    OnFailRollbackParameters parameters = extractParameters(advisingEvent);
    boolean canAdvise = StatusUtils.brokeStatuses().contains(advisingEvent.getToStatus());
    List<FailureType> failureTypesList = getAllFailureTypes(advisingEvent);
    if (!isEmpty(failureTypesList)) {
      return canAdvise && !Collections.disjoint(parameters.getApplicableFailureTypes(), failureTypesList);
    }
    return canAdvise;
  }

  @NotNull
  private OnFailRollbackParameters extractParameters(AdvisingEvent advisingEvent) {
    return (OnFailRollbackParameters) Preconditions.checkNotNull(
        kryoSerializer.asObject(advisingEvent.getAdviserParameters()));
  }
}
