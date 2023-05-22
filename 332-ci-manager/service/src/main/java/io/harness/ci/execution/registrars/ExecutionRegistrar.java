/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.registrars;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.states.ACRStep;
import io.harness.ci.states.ActionStep;
import io.harness.ci.states.BackgroundStep;
import io.harness.ci.states.BitriseStep;
import io.harness.ci.states.CISpecStep;
import io.harness.ci.states.CleanupStep;
import io.harness.ci.states.DockerStep;
import io.harness.ci.states.ECRStep;
import io.harness.ci.states.GCRStep;
import io.harness.ci.states.GitCloneStep;
import io.harness.ci.states.InitializeTaskStep;
import io.harness.ci.states.IntegrationStageStepPMS;
import io.harness.ci.states.PluginStep;
import io.harness.ci.states.RestoreCacheGCSStep;
import io.harness.ci.states.RestoreCacheS3Step;
import io.harness.ci.states.RunStep;
import io.harness.ci.states.RunTestsStep;
import io.harness.ci.states.SaveCacheGCSStep;
import io.harness.ci.states.SaveCacheS3Step;
import io.harness.ci.states.SecurityStep;
import io.harness.ci.states.UploadToArtifactoryStep;
import io.harness.ci.states.UploadToGCSStep;
import io.harness.ci.states.UploadToS3Step;
import io.harness.ci.states.V1.InitializeTaskStepV2;
import io.harness.ci.states.codebase.CodeBaseStep;
import io.harness.ci.states.codebase.CodeBaseTaskStep;
import io.harness.ci.states.ssca.SscaEnforcementStep;
import io.harness.ci.states.ssca.SscaOrchestrationStep;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.registrar.NGCommonUtilStepsRegistrar;
import io.harness.sto.STOStepType;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.CI)
public class ExecutionRegistrar {
  public static Map<StepType, Class<? extends Step>> getEngineSteps() {
    Map<StepType, Class<? extends Step>> engineSteps = new HashMap<>();

    //    engineSteps.put(InitializeTaskStep.STEP_TYPE, InitializeTaskStep.class);
    engineSteps.put(InitializeTaskStep.STEP_TYPE, InitializeTaskStepV2.class);
    engineSteps.put(CleanupStep.STEP_TYPE, CleanupStep.class);
    engineSteps.put(RunStep.STEP_TYPE, RunStep.class);
    engineSteps.put(BackgroundStep.STEP_TYPE, BackgroundStep.class);
    engineSteps.put(PluginStep.STEP_TYPE, PluginStep.class);
    engineSteps.put(GitCloneStep.STEP_TYPE, GitCloneStep.class);
    engineSteps.put(SecurityStep.STEP_TYPE, SecurityStep.class);
    engineSteps.putAll(STOStepType.addSTOEngineSteps(SecurityStep.class));
    engineSteps.put(ECRStep.STEP_TYPE, ECRStep.class);
    engineSteps.put(GCRStep.STEP_TYPE, GCRStep.class);
    engineSteps.put(ACRStep.STEP_TYPE, ACRStep.class);
    engineSteps.put(DockerStep.STEP_TYPE, DockerStep.class);
    engineSteps.put(UploadToS3Step.STEP_TYPE, UploadToS3Step.class);
    engineSteps.put(SaveCacheS3Step.STEP_TYPE, SaveCacheS3Step.class);
    engineSteps.put(RestoreCacheS3Step.STEP_TYPE, RestoreCacheS3Step.class);
    engineSteps.put(UploadToGCSStep.STEP_TYPE, UploadToGCSStep.class);
    engineSteps.put(SaveCacheGCSStep.STEP_TYPE, SaveCacheGCSStep.class);
    engineSteps.put(RestoreCacheGCSStep.STEP_TYPE, RestoreCacheGCSStep.class);
    engineSteps.put(UploadToArtifactoryStep.STEP_TYPE, UploadToArtifactoryStep.class);
    engineSteps.put(RunTestsStep.STEP_TYPE, RunTestsStep.class);
    engineSteps.put(IntegrationStageStepPMS.STEP_TYPE, IntegrationStageStepPMS.class);
    engineSteps.put(CodeBaseStep.STEP_TYPE, CodeBaseStep.class);
    engineSteps.put(CodeBaseTaskStep.STEP_TYPE, CodeBaseTaskStep.class);
    engineSteps.put(ActionStep.STEP_TYPE, ActionStep.class);
    engineSteps.put(BitriseStep.STEP_TYPE, BitriseStep.class);
    engineSteps.put(CISpecStep.STEP_TYPE, CISpecStep.class);
    engineSteps.put(SscaOrchestrationStep.STEP_TYPE, SscaOrchestrationStep.class);
    engineSteps.put(SscaEnforcementStep.STEP_TYPE, SscaEnforcementStep.class);
    engineSteps.putAll(NGCommonUtilStepsRegistrar.getEngineSteps());
    return engineSteps;
  }
}
