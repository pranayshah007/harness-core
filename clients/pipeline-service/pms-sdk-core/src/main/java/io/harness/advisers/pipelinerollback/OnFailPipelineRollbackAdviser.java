/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.advisers.pipelinerollback;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.advisers.CommonAdviserTypes;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.NextStepAdvise;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
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
public class OnFailPipelineRollbackAdviser implements Adviser {
  @Inject private KryoSerializer kryoSerializer;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  public static final AdviserType ADVISER_TYPE =
      AdviserType.newBuilder().setType(CommonAdviserTypes.ON_FAIL_PIPELINE_ROLLBACK.name()).build();

  @Override
  public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
    // this output will be picked up by NextStageAdvisor. NextStageAdvisor will decide whether to set next stage as
    // Pipeline Rollback Stage or the next stage in the pipeline yaml based on the existence of this sweeping output
    executionSweepingOutputService.consumeOptional(advisingEvent.getAmbiance(),
        YAMLFieldNameConstants.USE_PIPELINE_ROLLBACK_STRATEGY,
        OnFailPipelineRollbackOutput.builder().shouldStartPipelineRollback(true).build(),
        StepOutcomeGroup.PIPELINE.name());
    executionSweepingOutputService.consumeOptional(advisingEvent.getAmbiance(),
        YAMLFieldNameConstants.PIPELINE_ROLLBACK_FAILURE_INFO,
        OnFailPipelineRollbackOutput.builder()
            .shouldStartPipelineRollback(true)
            .levelsAtFailurePoint(advisingEvent.getAmbiance().getLevelsList())
            .build(),
        StepOutcomeGroup.STAGE.name());
    return AdviserResponse.newBuilder()
        .setNextStepAdvise(NextStepAdvise.newBuilder().build())
        .setType(AdviseType.NEXT_STEP)
        .build();
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    if (!StatusUtils.brokeStatuses().contains(advisingEvent.getToStatus())) {
      return false;
    }
    OnFailPipelineRollbackParameters parameters = extractParameters(advisingEvent);
    List<FailureType> failureTypesList = getAllFailureTypes(advisingEvent);
    return isEmpty(failureTypesList) || !Collections.disjoint(parameters.getApplicableFailureTypes(), failureTypesList);
  }

  @NotNull
  private OnFailPipelineRollbackParameters extractParameters(AdvisingEvent advisingEvent) {
    return (OnFailPipelineRollbackParameters) Preconditions.checkNotNull(
        kryoSerializer.asObject(advisingEvent.getAdviserParameters()));
  }
}
