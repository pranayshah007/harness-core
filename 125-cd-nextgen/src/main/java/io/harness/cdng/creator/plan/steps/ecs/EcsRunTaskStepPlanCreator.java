/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.ecs;

import static io.harness.cdng.visitor.YamlTypes.ECS_RUN_TASK;

import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.ecs.EcsRunTaskStepNode;
import io.harness.cdng.ecs.EcsRunTaskStepParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

public class EcsRunTaskStepPlanCreator extends CDPMSStepPlanCreatorV2<EcsRunTaskStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(ECS_RUN_TASK);
  }

  @Override
  public Class<EcsRunTaskStepNode> getFieldClass() {
    return EcsRunTaskStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, EcsRunTaskStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, EcsRunTaskStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);
    EcsRunTaskStepParameters ecsRunTaskStepParameters =
        (EcsRunTaskStepParameters) ((StepElementParameters) stepParameters).getSpec();
    ecsRunTaskStepParameters.setTaskDefinition(stepElement.getEcsRunTaskStepInfo().getTaskDefinition());
    ecsRunTaskStepParameters.setRunTaskRequestDefinition(
        stepElement.getEcsRunTaskStepInfo().getRunTaskRequestDefinition());
    ecsRunTaskStepParameters.setSkipSteadyStateCheck(stepElement.getEcsRunTaskStepInfo().getSkipSteadyStateCheck());
    ecsRunTaskStepParameters.setDelegateSelectors(stepElement.getEcsRunTaskStepInfo().getDelegateSelectors());
    return stepParameters;
  }
}
