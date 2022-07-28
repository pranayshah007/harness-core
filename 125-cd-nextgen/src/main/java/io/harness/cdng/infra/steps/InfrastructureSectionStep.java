/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.InfraStepUtils;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.ChildExecutableWithRbac;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class InfrastructureSectionStep implements ChildExecutableWithRbac<InfraSectionStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.INFRASTRUCTURE_SECTION.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;

  @Override
  public void validateResources(Ambiance ambiance, InfraSectionStepParameters stepParameters) {
    InfraStepUtils.validateResources(accessControlClient, ambiance, stepParameters);
  }

  @Override
  public Class<InfraSectionStepParameters> getStepParametersClass() {
    return InfraSectionStepParameters.class;
  }

  @Override
  public ChildExecutableResponse obtainChildAfterRbac(
      Ambiance ambiance, InfraSectionStepParameters stepParameters, StepInputPackage inputPackage) {
    log.info("Starting execution for InfraSection Step [{}]", stepParameters);
    return ChildExecutableResponse.newBuilder().setChildNodeId(stepParameters.getChildNodeID()).build();
  }

  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, InfraSectionStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Completed execution for InfraSection Step [{}]", stepParameters);
    return createStepResponseFromChildResponse(responseDataMap);
  }
}
