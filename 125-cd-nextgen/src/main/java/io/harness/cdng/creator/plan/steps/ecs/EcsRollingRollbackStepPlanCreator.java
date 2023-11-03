/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.ecs;

import static io.harness.cdng.visitor.YamlTypes.ECS_ROLLING_DEPLOY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.ecs.EcsRollingRollbackStep;
import io.harness.cdng.ecs.EcsRollingRollbackStepNode;
import io.harness.cdng.ecs.EcsRollingRollbackStepParameters;
import io.harness.cdng.ecs.asyncsteps.EcsRollingRollbackStepV2;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class EcsRollingRollbackStepPlanCreator extends CDPMSStepPlanCreatorV2<EcsRollingRollbackStepNode> {
  @Inject private CDFeatureFlagHelper featureFlagService;

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.ECS_ROLLING_ROLLBACK);
  }

  @Override
  public Class<EcsRollingRollbackStepNode> getFieldClass() {
    return EcsRollingRollbackStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, EcsRollingRollbackStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, EcsRollingRollbackStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    String ecsRollbackFnq = getExecutionStepFqn(ctx.getCurrentField(), ECS_ROLLING_DEPLOY);
    EcsRollingRollbackStepParameters ecsRollingRollbackStepParameters =
        (EcsRollingRollbackStepParameters) ((StepElementParameters) stepParameters).getSpec();
    ecsRollingRollbackStepParameters.setEcsRollingRollbackFnq(ecsRollbackFnq);
    ecsRollingRollbackStepParameters.setDelegateSelectors(
        stepElement.getEcsRollingRollbackStepInfo().getDelegateSelectors());
    return stepParameters;
  }

  @Override
  protected StepType getStepSpecType(PlanCreationContext ctx, EcsRollingRollbackStepNode stepElement) {
    if (featureFlagService.isEnabled(ctx.getAccountIdentifier(), FeatureName.CDS_ECS_ASYNC_STEP_STRATEGY)) {
      return EcsRollingRollbackStepV2.STEP_TYPE;
    }
    return EcsRollingRollbackStep.STEP_TYPE;
  }

  @Override
  protected String getFacilitatorType(PlanCreationContext ctx, EcsRollingRollbackStepNode stepElement) {
    if (featureFlagService.isEnabled(
            ctx.getMetadata().getAccountIdentifier(), FeatureName.CDS_ECS_ASYNC_STEP_STRATEGY)) {
      return OrchestrationFacilitatorType.ASYNC;
    }
    return OrchestrationFacilitatorType.TASK;
  }
}
