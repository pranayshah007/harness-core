/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.ServiceStepUtils;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.EntityReferenceExtractorUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;

@OwnedBy(HarnessTeam.CDC)
// This step only produces an Outcome for service expressions to work
public class ServiceStepV2 implements SyncExecutable<ServiceStepParametersV2> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(ExecutionNodeType.SERVICE_V2.getName()).setStepCategory(StepCategory.STEP).build();
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject private PipelineRbacHelper pipelineRbacHelper;

  @Override
  public Class<ServiceStepParametersV2> getStepParametersClass() {
    return ServiceStepParametersV2.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, ServiceStepParametersV2 stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    ServiceStepUtils.validateResourcesV2(
        entityReferenceExtractorUtils, pipelineRbacHelper, accessControlClient, ambiance, stepParameters);
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.SERVICE)
                         .outcome(ServiceStepOutcome.fromServiceStepV2(stepParameters.getIdentifier(),
                             stepParameters.getName(), stepParameters.getType(), stepParameters.getDescription(),
                             stepParameters.getTags(), stepParameters.getGitOpsEnabled()))
                         .group(StepCategory.STAGE.name())
                         .build())
        .build();
  }
}
