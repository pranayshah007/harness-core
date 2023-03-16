/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.common.rollback;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.steps.common.NGSectionStep;
import io.harness.steps.common.NGSectionStepParameters;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class RollbackStageStep extends NGSectionStep {
  ExecutionSweepingOutputService executionSweepingOutputService;

  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE)
                                               .setStepCategory(StepCategory.STAGE)
                                               .build();

  public static final String ROLLBACK_OUTPUT_KEY = "rollback";

  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, NGSectionStepParameters stepParameters, StepInputPackage inputPackage) {
    executionSweepingOutputService.consumeOptional(ambiance, ROLLBACK_OUTPUT_KEY,
        RollbackStageSweepingOutput.builder().isPipelineRollback(true).build(), StepOutcomeGroup.PIPELINE.name());
    return super.obtainChild(ambiance, stepParameters, inputPackage);
  }
}
