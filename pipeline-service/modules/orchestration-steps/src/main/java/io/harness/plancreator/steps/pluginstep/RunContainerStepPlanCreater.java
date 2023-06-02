/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.pluginstep;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.plugin.ContainerCommandUnitConstants;

import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class RunContainerStepPlanCreater {
  public PlanNode createPlanForField(
      String runStepNodeId, StepParameters stepElementParameters, List<AdviserObtainment> adviserObtainments) {
    return PlanNode.builder()
        .uuid(runStepNodeId)
        .name(ContainerCommandUnitConstants.ContainerStep)
        .identifier(ContainerCommandUnitConstants.ContainerStep.replaceAll("\\s", ""))
        .stepType(StepType.newBuilder()
                      .setType(StepSpecTypeConstants.RUN_CONTAINER_STEP)
                      .setStepCategory(StepCategory.STEP)
                      .build())
        .adviserObtainments(adviserObtainments)
        .group(StepOutcomeGroup.STEP.name())
        .stepParameters(stepElementParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                .build())
        .skipExpressionChain(false)
        .skipGraphType(SkipType.NOOP)
        .build();
  }
}
