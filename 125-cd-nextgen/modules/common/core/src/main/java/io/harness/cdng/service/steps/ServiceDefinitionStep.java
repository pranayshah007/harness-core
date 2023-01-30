/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.SdkCoreStepUtils;
import io.harness.tasks.ResponseData;

import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class ServiceDefinitionStep implements ChildExecutable<ServiceDefinitionStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.SERVICE_DEFINITION.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Override
  public Class<ServiceDefinitionStepParameters> getStepParametersClass() {
    return ServiceDefinitionStepParameters.class;
  }

  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, ServiceDefinitionStepParameters stepParameters, StepInputPackage inputPackage) {
    return ChildExecutableResponse.newBuilder().setChildNodeId(stepParameters.getChildNodeId()).build();
  }

  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, ServiceDefinitionStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    return SdkCoreStepUtils.createStepResponseFromChildResponse(responseDataMap);
  }
}
