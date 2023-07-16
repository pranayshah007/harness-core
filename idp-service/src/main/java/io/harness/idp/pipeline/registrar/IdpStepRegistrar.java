/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.pipeline.registrar;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.states.*;
import io.harness.ci.states.V1.InitializeTaskStepV2;
import io.harness.ci.states.codebase.CodeBaseStep;
import io.harness.ci.states.codebase.CodeBaseTaskStep;
import io.harness.idp.pipeline.stages.step.IDPSpecStep;
import io.harness.idp.pipeline.stages.step.IDPStageStepPMS;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.registrar.NGCommonUtilStepsRegistrar;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class IdpStepRegistrar {
  public static Map<StepType, Class<? extends Step>> getEngineSteps() {
    Map<StepType, Class<? extends Step>> engineSteps = new HashMap<>();

    engineSteps.put(InitializeTaskStep.STEP_TYPE, InitializeTaskStepV2.class);
    engineSteps.put(CleanupStep.STEP_TYPE, CleanupStep.class);
    engineSteps.put(RunStep.STEP_TYPE, RunStep.class);
    engineSteps.put(PluginStep.STEP_TYPE, PluginStep.class);
    engineSteps.put(GitCloneStep.STEP_TYPE, GitCloneStep.class);
    //    engineSteps.put(IntegrationStageStepPMS.STEP_TYPE, IntegrationStageStepPMS.class);
    //    engineSteps.put(CISpecStep.STEP_TYPE, CISpecStep.class);
    engineSteps.put(IDPStageStepPMS.STEP_TYPE, IDPStageStepPMS.class);
    engineSteps.put(IDPSpecStep.STEP_TYPE, IDPSpecStep.class);
    engineSteps.put(CodeBaseStep.STEP_TYPE, CodeBaseStep.class);
    engineSteps.put(CodeBaseTaskStep.STEP_TYPE, CodeBaseTaskStep.class);
    engineSteps.putAll(NGCommonUtilStepsRegistrar.getEngineSteps());
    return engineSteps;
  }
}
