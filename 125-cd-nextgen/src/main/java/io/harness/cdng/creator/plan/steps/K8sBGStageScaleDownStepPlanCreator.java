/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.K8sBGStageScaleDownStep;
import io.harness.cdng.k8s.K8sBGStageScaleDownStepNode;
import io.harness.cdng.k8s.asyncsteps.K8sBGStageScaleDownStepV2;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Set;

@OwnedBy(CDP)
public class K8sBGStageScaleDownStepPlanCreator extends CDPMSStepPlanCreatorV2<K8sBGStageScaleDownStepNode> {
  @Inject private CDFeatureFlagHelper featureFlagService;
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.K8S_BLUE_GREEN_STAGE_SCALE_DOWN);
  }

  @Override
  public Class<K8sBGStageScaleDownStepNode> getFieldClass() {
    return K8sBGStageScaleDownStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, K8sBGStageScaleDownStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  public StepType getStepSpecType(PlanCreationContext ctx, K8sBGStageScaleDownStepNode stepElement) {
    if (featureFlagService.isEnabled(
            ctx.getMetadata().getAccountIdentifier(), FeatureName.CDS_K8S_ASYNC_STEP_STRATEGY)) {
      return K8sBGStageScaleDownStepV2.STEP_TYPE;
    }
    return K8sBGStageScaleDownStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType(PlanCreationContext ctx, K8sBGStageScaleDownStepNode stepElement) {
    if (featureFlagService.isEnabled(
            ctx.getMetadata().getAccountIdentifier(), FeatureName.CDS_K8S_ASYNC_STEP_STRATEGY)) {
      return OrchestrationFacilitatorType.ASYNC;
    }
    return OrchestrationFacilitatorType.TASK;
  }
}
