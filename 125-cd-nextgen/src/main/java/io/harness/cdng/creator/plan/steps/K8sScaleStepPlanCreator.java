/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.K8sScaleStep;
import io.harness.cdng.k8s.K8sScaleStepNode;
import io.harness.cdng.k8s.asyncsteps.K8sScaleStepV2;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Set;

@OwnedBy(CDP)
public class K8sScaleStepPlanCreator extends CDPMSStepPlanCreatorV2<K8sScaleStepNode> {
  @Inject private CDFeatureFlagHelper featureFlagService;

  @Override
  protected String getFacilitatorType(PlanCreationContext ctx, K8sScaleStepNode stepElement) {
    if (featureFlagService.isEnabled(
            ctx.getMetadata().getAccountIdentifier(), FeatureName.CDS_K8S_ASYNC_STEP_STRATEGY)) {
      return OrchestrationFacilitatorType.ASYNC;
    }
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  protected StepType getStepSpecType(PlanCreationContext ctx, K8sScaleStepNode stepElement) {
    if (featureFlagService.isEnabled(ctx.getAccountIdentifier(), FeatureName.CDS_K8S_ASYNC_STEP_STRATEGY)) {
      return K8sScaleStepV2.STEP_TYPE;
    }
    return K8sScaleStep.STEP_TYPE;
  }

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.K8S_SCALE);
  }

  @Override
  public Class<K8sScaleStepNode> getFieldClass() {
    return K8sScaleStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, K8sScaleStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }
}
