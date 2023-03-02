/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.registars;

import io.harness.ci.states.CISpecStep;
import io.harness.ci.states.CleanupStep;
import io.harness.ci.states.IACMStep;
import io.harness.ci.states.InitializeTaskStep;
import io.harness.ci.states.IntegrationStageStepPMS;
import io.harness.ci.states.PluginStep;
import io.harness.ci.states.RunStep;
import io.harness.ci.states.V1.InitializeTaskStepV2;
import io.harness.ci.states.codebase.CodeBaseStep;
import io.harness.ci.states.codebase.CodeBaseTaskStep;
import io.harness.iacm.IACMStepType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.registrar.NGCommonUtilStepsRegistrar;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class IACMExecutionRegistrar {
  private static Map<StepType, Class<? extends Step>> addIACMEngineSteps() {
    Map<StepType, Class<? extends Step>> iacmSteps = new HashMap<>();

    Arrays.asList(IACMStepType.values()).forEach(e -> iacmSteps.put(e.getStepType(), IACMStep.class));

    return iacmSteps;
  }
  public static Map<StepType, Class<? extends Step>> getEngineSteps() {
    Map<StepType, Class<? extends Step>> engineSteps = new HashMap<>();

    engineSteps.put(InitializeTaskStep.STEP_TYPE, InitializeTaskStepV2.class);
    engineSteps.put(IntegrationStageStepPMS.STEP_TYPE,
        IntegrationStageStepPMS.class); // This seems to be the STAGE STEP per ser. So the stage is treated as a STEP
    engineSteps.put(CISpecStep.STEP_TYPE,
        CISpecStep.class); // No idea why I need this step, apart from been used in the IACMStagePMSPlanCreator.
    engineSteps.put(CleanupStep.STEP_TYPE, CleanupStep.class);
    engineSteps.put(PluginStep.STEP_TYPE, PluginStep.class);
    engineSteps.put(CodeBaseStep.STEP_TYPE, CodeBaseStep.class);
    engineSteps.put(CodeBaseTaskStep.STEP_TYPE, CodeBaseTaskStep.class);
    engineSteps.putAll(addIACMEngineSteps());
    engineSteps.put(RunStep.STEP_TYPE, RunStep.class);
    engineSteps.putAll(NGCommonUtilStepsRegistrar.getEngineSteps());
    return engineSteps;
  }
}
