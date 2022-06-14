/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.environment.steps;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.environment.EnvironmentMapper;
import io.harness.cdng.creator.plan.environment.EnvironmentStepsUtils;
import io.harness.cdng.environment.steps.EnvironmentStepParameters;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.ng.core.envGroup.EnvironmentGroupOutcome;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.steps.executable.SyncExecutableWithRbac;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class EnvironmentStepV2 implements SyncExecutableWithRbac<EnvironmentStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ENVIRONMENT_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject ExecutionSweepingOutputService executionSweepingOutputResolver;

  @Override
  public void validateResources(Ambiance ambiance, EnvironmentStepParameters stepParameters) {
    EnvironmentStepsUtils.validateResources(accessControlClient, ambiance, stepParameters);
  }

  @Override
  public StepResponse executeSyncAfterRbac(Ambiance ambiance, EnvironmentStepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    log.info("Starting execution for Environment Step [{}]", stepParameters);
    if (stepParameters.getEnvGroupRef() != null && stepParameters.getEnvGroupRef().fetchFinalValue() != null) {
      EnvironmentGroupOutcome environmentGroupOutcome = EnvironmentMapper.toEnvironmentGroupOutcome(stepParameters);
      executionSweepingOutputResolver.consume(
          ambiance, OutputExpressionConstants.ENVIRONMENT_GROUP, environmentGroupOutcome, StepCategory.STAGE.name());
    } else if (stepParameters.getEnvironmentRef() != null
        && stepParameters.getEnvironmentRef().fetchFinalValue() != null) {
      EnvironmentOutcome environmentOutcome = EnvironmentMapper.toEnvironmentOutcome(stepParameters);
      executionSweepingOutputResolver.consume(
          ambiance, OutputExpressionConstants.ENVIRONMENT, environmentOutcome, StepCategory.STAGE.name());
    } else {
      return StepResponse.builder()
          .status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder()
                           .addFailureData(FailureData.newBuilder().setMessage("env or env group must be set").build())
                           .build())
          .build();
    }
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  @Override
  public Class<EnvironmentStepParameters> getStepParametersClass() {
    return EnvironmentStepParameters.class;
  }
}
