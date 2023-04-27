/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.advisers.retry;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.contracts.execution.Status.INTERVENTION_WAITING;
import static io.harness.pms.execution.utils.StatusUtils.retryableStatuses;

import io.harness.advisers.CommonAdviserTypes;
import io.harness.advisers.rollback.OnFailRollbackOutput;
import io.harness.advisers.rollback.RollbackStrategy;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.EndPlanAdvise;
import io.harness.pms.contracts.advisers.IgnoreFailureAdvise;
import io.harness.pms.contracts.advisers.InterventionWaitAdvise;
import io.harness.pms.contracts.advisers.MarkAsFailureAdvise;
import io.harness.pms.contracts.advisers.MarkSuccessAdvise;
import io.harness.pms.contracts.advisers.NextStepAdvise;
import io.harness.pms.contracts.advisers.RetryAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.KryoSerializer;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.protobuf.Duration;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class RetryAdviserWithRollback implements Adviser {
  @Inject private KryoSerializer kryoSerializer;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  public static final AdviserType ADVISER_TYPE =
      AdviserType.newBuilder().setType(CommonAdviserTypes.RETRY_WITH_ROLLBACK.name()).build();

  @Override
  public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
    RetryAdviserRollbackParameters parameters = extractParameters(advisingEvent);
    int retryCount = advisingEvent.getRetryCount();

    if (retryCount < parameters.getRetryCount()) {
      int waitInterval = calculateWaitInterval(parameters.getWaitIntervalList(), retryCount);
      return AdviserResponse.newBuilder()
          .setType(AdviseType.RETRY)
          .setRetryAdvise(
              RetryAdvise.newBuilder()
                  .setRetryNodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(advisingEvent.getAmbiance()))
                  .setWaitInterval(waitInterval)
                  .build())
          .build();
    }
    return handlePostRetry(parameters, advisingEvent.getAmbiance(), advisingEvent.getToStatus());
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    boolean canAdvise = retryableStatuses().contains(advisingEvent.getToStatus())
        && advisingEvent.getFromStatus() != INTERVENTION_WAITING;
    RetryAdviserRollbackParameters parameters = extractParameters(advisingEvent);
    List<FailureType> failureTypesList = getAllFailureTypes(advisingEvent);
    if (parameters != null && !isEmpty(failureTypesList)) {
      return canAdvise && !Collections.disjoint(parameters.getApplicableFailureTypes(), failureTypesList);
    }
    return canAdvise;
  }

  private AdviserResponse handlePostRetry(
      RetryAdviserRollbackParameters parameters, Ambiance ambiance, Status toStatus) {
    AdviserResponse.Builder adviserResponseBuilder =
        AdviserResponse.newBuilder().setRepairActionCode(parameters.getRepairActionCodeAfterRetry());
    switch (parameters.getRepairActionCodeAfterRetry()) {
      case MANUAL_INTERVENTION:
        return adviserResponseBuilder
            .setInterventionWaitAdvise(
                InterventionWaitAdvise.newBuilder()
                    .setTimeout(Duration.newBuilder().setSeconds(java.time.Duration.ofDays(1).toMinutes() * 60).build())
                    .setFromStatus(toStatus)
                    .build())
            .setType(AdviseType.INTERVENTION_WAIT)
            .build();
      case END_EXECUTION:
        return adviserResponseBuilder.setEndPlanAdvise(EndPlanAdvise.newBuilder().setIsAbort(true).build())
            .setType(AdviseType.END_PLAN)
            .build();
      case IGNORE:
        return adviserResponseBuilder.setIgnoreFailureAdvise(IgnoreFailureAdvise.newBuilder().build())
            .setType(AdviseType.IGNORE_FAILURE)
            .build();
      case STAGE_ROLLBACK:
      case STEP_GROUP_ROLLBACK:
        String nextNodeId = parameters.getStrategyToUuid().get(
            RollbackStrategy.fromRepairActionCode(parameters.getRepairActionCodeAfterRetry()));
        executionSweepingOutputService.consume(ambiance, YAMLFieldNameConstants.USE_ROLLBACK_STRATEGY,
            OnFailRollbackOutput.builder().nextNodeId(nextNodeId).build(), StepOutcomeGroup.STEP.name());
        try {
          executionSweepingOutputService.consume(ambiance, YAMLFieldNameConstants.STOP_STEPS_SEQUENCE,
              OnFailRollbackOutput.builder().nextNodeId(nextNodeId).build(), StepCategory.STAGE.name());
        } catch (Exception e) {
          log.warn("Ignoring duplicate sweeping output of - " + YAMLFieldNameConstants.STOP_STEPS_SEQUENCE);
        }
        NextStepAdvise.Builder nextStepAdvise = NextStepAdvise.newBuilder();
        return adviserResponseBuilder.setNextStepAdvise(nextStepAdvise.build()).setType(AdviseType.NEXT_STEP).build();
      case ON_FAIL:
        nextStepAdvise = NextStepAdvise.newBuilder();
        return adviserResponseBuilder.setNextStepAdvise(nextStepAdvise.build()).setType(AdviseType.NEXT_STEP).build();
      case MARK_AS_SUCCESS:
        MarkSuccessAdvise.Builder markSuccessBuilder = MarkSuccessAdvise.newBuilder();
        if (EmptyPredicate.isNotEmpty(parameters.getNextNodeId())) {
          markSuccessBuilder.setNextNodeId(parameters.getNextNodeId());
        }
        return adviserResponseBuilder.setMarkSuccessAdvise(markSuccessBuilder.build())
            .setType(AdviseType.MARK_SUCCESS)
            .build();
      case MARK_AS_FAILURE:
        MarkAsFailureAdvise.Builder builder = MarkAsFailureAdvise.newBuilder();
        if (EmptyPredicate.isNotEmpty(parameters.getNextNodeId())) {
          builder.setNextNodeId(parameters.getNextNodeId());
        }
        return AdviserResponse.newBuilder()
            .setMarkAsFailureAdvise(builder.build())
            .setType(AdviseType.MARK_AS_FAILURE)
            .build();
      default:
        throw new IllegalStateException("Unexpected value: " + parameters.getRepairActionCodeAfterRetry());
    }
  }

  private int calculateWaitInterval(List<Integer> waitIntervalList, int retryCount) {
    if (isEmpty(waitIntervalList)) {
      return 0;
    }
    return waitIntervalList.size() <= retryCount ? waitIntervalList.get(waitIntervalList.size() - 1)
                                                 : waitIntervalList.get(retryCount);
  }

  @NotNull
  private RetryAdviserRollbackParameters extractParameters(AdvisingEvent advisingEvent) {
    return (RetryAdviserRollbackParameters) Preconditions.checkNotNull(
        kryoSerializer.asObject(advisingEvent.getAdviserParameters()));
  }
}
