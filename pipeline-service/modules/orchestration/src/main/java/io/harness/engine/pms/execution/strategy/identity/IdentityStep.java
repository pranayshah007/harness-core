/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.identity;

import static io.harness.plan.NodeType.IDENTITY_PLAN_NODE;

import io.harness.OrchestrationStepTypes;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.engine.pms.steps.identity.IdentityStepParameters;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.executables.ChildrenExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;

@OwnedBy(HarnessTeam.PIPELINE)
public class IdentityStep
    implements ChildExecutable<IdentityStepParameters>, ChildrenExecutable<IdentityStepParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(OrchestrationStepTypes.IDENTITY_STEP).setStepCategory(StepCategory.STEP).build();

  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PmsOutcomeService pmsOutcomeService;
  @Inject private PmsSweepingOutputService pmsSweepingOutputService;
  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, IdentityStepParameters identityParams, StepInputPackage inputPackage) {
    NodeExecution originalNodeExecution = nodeExecutionService.getWithFieldsIncluded(
        identityParams.getOriginalNodeExecutionId(), NodeProjectionUtils.withExecutableResponses);
    // Copying the outputs
    pmsSweepingOutputService.cloneForRetryExecution(ambiance, identityParams.getOriginalNodeExecutionId());
    return originalNodeExecution.getExecutableResponses().get(0).getChild();
  }

  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, IdentityStepParameters identityParams, Map<String, ResponseData> responseDataMap) {
    NodeExecution originalNodeExecution = nodeExecutionService.get(identityParams.getOriginalNodeExecutionId());
    // Copying the outcomes
    pmsOutcomeService.cloneForRetryExecution(ambiance, originalNodeExecution.getUuid());
    return StepResponse.builder().status(originalNodeExecution.getStatus()).build();
  }

  @Override
  public ChildrenExecutableResponse obtainChildren(
      Ambiance ambiance, IdentityStepParameters identityParams, StepInputPackage inputPackage) {
    NodeExecution originalNodeExecution = nodeExecutionService.get(identityParams.getOriginalNodeExecutionId());
    // Copying the outputs here
    pmsSweepingOutputService.cloneForRetryExecution(ambiance, originalNodeExecution.getUuid());
    return originalNodeExecution.getExecutableResponses().get(0).getChildren();
  }

  @Override
  public StepResponse handleChildrenResponse(
      Ambiance ambiance, IdentityStepParameters identityParams, Map<String, ResponseData> responseDataMap) {
    NodeExecution originalNodeExecution = nodeExecutionService.get(identityParams.getOriginalNodeExecutionId());
    // copying the outcomes
    pmsOutcomeService.cloneForRetryExecution(ambiance, originalNodeExecution.getUuid());
    return StepResponse.builder().status(originalNodeExecution.getStatus()).build();
  }

  @Override
  public Class<IdentityStepParameters> getStepParametersClass() {
    return IdentityStepParameters.class;
  }

  public static Ambiance modifyAmbiance(Ambiance ambiance) {
    Level level = AmbianceUtils.obtainCurrentLevel(ambiance);
    StepCategory stepCategory = level.getStepType().getStepCategory();
    Optional<Level> strategyLevel = AmbianceUtils.getStrategyLevelFromAmbiance(ambiance);
    if (strategyLevel.isPresent() && strategyLevel.get().getNodeType().equals(IDENTITY_PLAN_NODE.name())) {
      String stepType = IdentityStrategyStep.STEP_TYPE.getType();
      if (stepCategory != StepCategory.STRATEGY) {
        stepType = IdentityStrategyInternalStep.STEP_TYPE.getType();
      }
      return AmbianceUtils.cloneForFinish(ambiance,
          level.toBuilder()
              .setStepType(StepType.newBuilder().setType(stepType).setStepCategory(StepCategory.STRATEGY).build())
              .build());
    }
    return AmbianceUtils.cloneForFinish(ambiance,
        level.toBuilder()
            .setStepType(StepType.newBuilder().setType("IDENTITY_STEP").setStepCategory(StepCategory.STEP).build())
            .build());
  }
}
