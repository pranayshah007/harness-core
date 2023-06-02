/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.registrar;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.steps.NGSpecStep;
import io.harness.steps.common.NGExecutionStep;
import io.harness.steps.common.NGSectionStep;
import io.harness.steps.common.NGSectionStepWithRollbackInfo;
import io.harness.steps.common.noop.NoopStep;
import io.harness.steps.common.steps.stepgroup.StepGroupStep;
import io.harness.steps.fork.NGForkStep;
import io.harness.steps.matrix.StrategyStep;
import io.harness.steps.matrix.v1.StrategyStepV1;
import io.harness.steps.section.chain.SectionChainStep;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class NGCommonUtilStepsRegistrar {
  public Map<StepType, Class<? extends Step>> getEngineSteps() {
    Map<StepType, Class<? extends Step>> engineSteps = new HashMap<>();
    engineSteps.put(NGForkStep.STEP_TYPE, NGForkStep.class);
    engineSteps.put(NGSectionStep.STEP_TYPE, NGSectionStep.class);
    engineSteps.put(NGSpecStep.STEP_TYPE, NGSpecStep.class);
    engineSteps.put(SectionChainStep.STEP_TYPE, SectionChainStep.class);
    engineSteps.put(NGSectionStepWithRollbackInfo.STEP_TYPE, NGSectionStepWithRollbackInfo.class);
    engineSteps.put(NGExecutionStep.STEP_TYPE, NGExecutionStep.class);
    engineSteps.put(StepGroupStep.STEP_TYPE, StepGroupStep.class);
    engineSteps.put(StrategyStep.STEP_TYPE, StrategyStep.class);
    engineSteps.put(StrategyStepV1.STEP_TYPE, StrategyStepV1.class);
    engineSteps.put(NoopStep.STEP_TYPE, NoopStep.class);
    return engineSteps;
  }
}
