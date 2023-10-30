/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s.asyncsteps;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.executables.CdAsyncExecutable;
import io.harness.cdng.k8s.K8sBGStageScaleDownStep;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
public class K8sBGStageScaleDownStepV2 extends CdAsyncExecutable<K8sDeployResponse, K8sBGStageScaleDownStep> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.K8S_BLUE_GREEN_STAGE_SCALE_DOWN_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
}
