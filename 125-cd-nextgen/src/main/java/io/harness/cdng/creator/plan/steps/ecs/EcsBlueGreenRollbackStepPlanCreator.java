/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.ecs;

import static io.harness.cdng.visitor.YamlTypes.ECS_BLUE_GREEN_CREATE_SERVICE;
import static io.harness.cdng.visitor.YamlTypes.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.ecs.EcsBlueGreenRollbackStep;
import io.harness.cdng.ecs.EcsBlueGreenRollbackStepNode;
import io.harness.cdng.ecs.EcsBlueGreenRollbackStepParameters;
import io.harness.cdng.ecs.asyncsteps.EcsBlueGreenRollbackStepV2;
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
public class EcsBlueGreenRollbackStepPlanCreator extends CDPMSStepPlanCreatorV2<EcsBlueGreenRollbackStepNode> {
  @Inject private CDFeatureFlagHelper featureFlagService;

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.ECS_BLUE_GREEN_ROLLBACK);
  }

  @Override
  public Class<EcsBlueGreenRollbackStepNode> getFieldClass() {
    return EcsBlueGreenRollbackStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, EcsBlueGreenRollbackStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, EcsBlueGreenRollbackStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    String ecsBlueGreenCreateServiceFnq = getExecutionStepFqn(ctx.getCurrentField(), ECS_BLUE_GREEN_CREATE_SERVICE);
    String ecsBlueGreenSwapTargetGroupsFnq =
        getExecutionStepFqn(ctx.getCurrentField(), ECS_BLUE_GREEN_SWAP_TARGET_GROUPS);
    EcsBlueGreenRollbackStepParameters ecsBlueGreenRollbackStepParameters =
        (EcsBlueGreenRollbackStepParameters) ((StepElementParameters) stepParameters).getSpec();
    ecsBlueGreenRollbackStepParameters.setEcsBlueGreenCreateServiceFnq(ecsBlueGreenCreateServiceFnq);
    ecsBlueGreenRollbackStepParameters.setEcsBlueGreenSwapTargetGroupsFnq(ecsBlueGreenSwapTargetGroupsFnq);
    return stepParameters;
  }

  @Override
  public StepType getStepSpecType(PlanCreationContext ctx, EcsBlueGreenRollbackStepNode stepElement) {
    if (featureFlagService.isEnabled(
            ctx.getMetadata().getAccountIdentifier(), FeatureName.CDS_ECS_ASYNC_STEP_STRATEGY)) {
      return EcsBlueGreenRollbackStepV2.STEP_TYPE;
    }
    return EcsBlueGreenRollbackStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType(PlanCreationContext ctx, EcsBlueGreenRollbackStepNode stepElement) {
    if (featureFlagService.isEnabled(
            ctx.getMetadata().getAccountIdentifier(), FeatureName.CDS_ECS_ASYNC_STEP_STRATEGY)) {
      return OrchestrationFacilitatorType.ASYNC;
    }
    return OrchestrationFacilitatorType.TASK;
  }
}
