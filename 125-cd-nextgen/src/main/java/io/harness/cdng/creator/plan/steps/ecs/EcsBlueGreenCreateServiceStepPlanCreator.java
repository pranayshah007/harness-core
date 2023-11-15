/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.ecs.EcsBlueGreenCreateServiceStepNode;
import io.harness.cdng.ecs.EcsCanaryDeployStep;
import io.harness.cdng.ecs.asyncsteps.EcsCanaryDeployStepV2;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class EcsBlueGreenCreateServiceStepPlanCreator
    extends CDPMSStepPlanCreatorV2<EcsBlueGreenCreateServiceStepNode> {
  @Inject private CDFeatureFlagHelper featureFlagService;

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.ECS_BLUE_GREEN_CREATE_SERVICE);
  }

  @Override
  public Class<EcsBlueGreenCreateServiceStepNode> getFieldClass() {
    return EcsBlueGreenCreateServiceStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(
      PlanCreationContext ctx, EcsBlueGreenCreateServiceStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepType getStepSpecType(PlanCreationContext ctx, EcsBlueGreenCreateServiceStepNode stepElement) {
    if (featureFlagService.isEnabled(
            ctx.getMetadata().getAccountIdentifier(), FeatureName.CDS_ECS_ASYNC_STEP_STRATEGY)) {
      return EcsCanaryDeployStepV2.STEP_TYPE;
    }
    return EcsCanaryDeployStep.STEP_TYPE;
  }

  @Override
  protected String getFacilitatorType(PlanCreationContext ctx, EcsBlueGreenCreateServiceStepNode stepElement) {
    if (featureFlagService.isEnabled(
            ctx.getMetadata().getAccountIdentifier(), FeatureName.CDS_ECS_ASYNC_STEP_STRATEGY)) {
      return OrchestrationFacilitatorType.ASYNC_CHAIN;
    }
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }
}
