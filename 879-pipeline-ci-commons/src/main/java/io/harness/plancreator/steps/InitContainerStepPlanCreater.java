/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.steps.plugin.ContainerCommandUnitConstants;

import com.google.protobuf.ByteString;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class InitContainerStepPlanCreater {
  public PlanNode createPlanForField(
      String runStepNodeId, StepParameters stepElementParameters, ByteString advisorParams, String stepType) {
    return PlanNode.builder()
        .uuid(runStepNodeId)
        .name(ContainerCommandUnitConstants.InitContainer)
        .identifier(ContainerCommandUnitConstants.InitContainer)
        .stepType(StepType.newBuilder().setType(stepType).setStepCategory(StepCategory.STEP).build())
        .group(StepOutcomeGroup.STEP.name())
        .stepParameters(stepElementParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.TASK).build())
                .build())
        .adviserObtainment(
            AdviserObtainment.newBuilder()
                .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                .setParameters(advisorParams)
                .build())
        .skipGraphType(SkipType.NOOP)
        .skipExpressionChain(false)
        .build();
  }
}
