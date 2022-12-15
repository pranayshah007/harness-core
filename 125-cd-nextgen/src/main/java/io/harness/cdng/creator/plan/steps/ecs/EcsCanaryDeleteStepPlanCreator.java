/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.ecs;

import static io.harness.cdng.visitor.YamlTypes.ECS_CANARY_DELETE;
import static io.harness.cdng.visitor.YamlTypes.ECS_CANARY_DEPLOY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.ecs.EcsCanaryDeleteStepNode;
import io.harness.cdng.ecs.EcsCanaryDeleteStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class EcsCanaryDeleteStepPlanCreator extends CDPMSStepPlanCreatorV2<EcsCanaryDeleteStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.ECS_CANARY_DELETE);
  }

  @Override
  public Class<EcsCanaryDeleteStepNode> getFieldClass() {
    return EcsCanaryDeleteStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, EcsCanaryDeleteStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, EcsCanaryDeleteStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    String ecsCanaryDeployFnq = getExecutionStepFqn(ctx.getCurrentField(), ECS_CANARY_DEPLOY);
    String ecsCanaryDeleteFnq = getExecutionStepFqn(ctx.getCurrentField(), ECS_CANARY_DELETE);
    EcsCanaryDeleteStepParameters ecsCanaryDeleteStepParameters =
        (EcsCanaryDeleteStepParameters) ((StepElementParameters) stepParameters).getSpec();
    ecsCanaryDeleteStepParameters.setEcsCanaryDeployFnq(ecsCanaryDeployFnq);
    ecsCanaryDeleteStepParameters.setEcsCanaryDeleteFnq(ecsCanaryDeleteFnq);

    return stepParameters;
  }
}
