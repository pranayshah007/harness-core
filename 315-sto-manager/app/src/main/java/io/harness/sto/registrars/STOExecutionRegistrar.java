/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sto.registrars;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.states.CleanupStep;
import io.harness.ci.states.InitializeTaskStep;
import io.harness.ci.states.RunStep;
import io.harness.ci.states.STOSpecStep;
import io.harness.ci.states.SecurityStageStepPMS;
import io.harness.ci.states.SecurityStep;
import io.harness.ci.states.V1.InitializeTaskStepV2;
import io.harness.ci.states.codebase.CodeBaseStep;
import io.harness.ci.states.codebase.CodeBaseTaskStep;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.registrar.NGCommonUtilStepsRegistrar;
import io.harness.sto.STOStepType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.STO)
public class STOExecutionRegistrar {
  private static Map<StepType, Class<? extends Step>> addSTOEngineSteps() {
    Map<StepType, Class<? extends Step>> stoSteps = new HashMap<>();

    Arrays.asList(STOStepType.values()).forEach(e -> stoSteps.put(e.getStepType(), SecurityStep.class));

    return stoSteps;
  }
  public static Map<StepType, Class<? extends Step>> getEngineSteps() {
    Map<StepType, Class<? extends Step>> engineSteps = new HashMap<>();

    engineSteps.put(InitializeTaskStep.STEP_TYPE, InitializeTaskStepV2.class);
    engineSteps.put(CleanupStep.STEP_TYPE, CleanupStep.class);
    engineSteps.put(RunStep.STEP_TYPE, RunStep.class);
    engineSteps.putAll(addSTOEngineSteps());
    engineSteps.put(SecurityStep.STEP_TYPE, SecurityStep.class);
    engineSteps.put(STOSpecStep.STEP_TYPE, STOSpecStep.class);
    engineSteps.put(SecurityStageStepPMS.STEP_TYPE, SecurityStageStepPMS.class);
    engineSteps.put(CodeBaseStep.STEP_TYPE, CodeBaseStep.class);
    engineSteps.put(CodeBaseTaskStep.STEP_TYPE, CodeBaseTaskStep.class);
    engineSteps.putAll(NGCommonUtilStepsRegistrar.getEngineSteps());
    return engineSteps;
  }
}
