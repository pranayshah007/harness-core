/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.registrars;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.registrar.NGCommonUtilStepsRegistrar;
import io.harness.states.CleanupStep;
import io.harness.states.InitializeTaskStep;
import io.harness.states.STOSpecStep;
import io.harness.states.SecurityStageStepPMS;
import io.harness.states.SecurityStep;
import io.harness.states.codebase.CodeBaseStep;
import io.harness.states.codebase.CodeBaseTaskStep;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.STO)
public class STOExecutionRegistrar {
  public Map<StepType, Class<? extends Step>> getEngineSteps() {
    Map<StepType, Class<? extends Step>> engineSteps = new HashMap<>();

    engineSteps.put(InitializeTaskStep.STEP_TYPE, InitializeTaskStep.class);
    engineSteps.put(CleanupStep.STEP_TYPE, CleanupStep.class);
    engineSteps.put(SecurityStep.STEP_TYPE, SecurityStep.class);
    engineSteps.put(STOSpecStep.STEP_TYPE, STOSpecStep.class);
    engineSteps.put(SecurityStageStepPMS.STEP_TYPE, SecurityStageStepPMS.class);
    engineSteps.put(CodeBaseStep.STEP_TYPE, CodeBaseStep.class);
    engineSteps.put(CodeBaseTaskStep.STEP_TYPE, CodeBaseTaskStep.class);
    engineSteps.putAll(NGCommonUtilStepsRegistrar.getEngineSteps());
    return engineSteps;
  }
}
