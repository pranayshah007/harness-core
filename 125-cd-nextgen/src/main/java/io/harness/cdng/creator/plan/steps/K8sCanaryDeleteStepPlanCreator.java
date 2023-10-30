/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.visitor.YamlTypes.K8S_CANARY_DELETE;
import static io.harness.cdng.visitor.YamlTypes.K8S_CANARY_DEPLOY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.K8sCanaryDeleteStep;
import io.harness.cdng.k8s.K8sCanaryDeleteStepNode;
import io.harness.cdng.k8s.K8sCanaryDeleteStepParameters;
import io.harness.cdng.k8s.asyncsteps.K8sCanaryDeleteStepV2;
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

@OwnedBy(CDP)
public class K8sCanaryDeleteStepPlanCreator extends CDPMSStepPlanCreatorV2<K8sCanaryDeleteStepNode> {
  @Inject private CDFeatureFlagHelper featureFlagService;

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.K8S_CANARY_DELETE);
  }

  @Override
  public Class<K8sCanaryDeleteStepNode> getFieldClass() {
    return K8sCanaryDeleteStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, K8sCanaryDeleteStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, K8sCanaryDeleteStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    String canaryStepFqn = getExecutionStepFqn(ctx.getCurrentField(), K8S_CANARY_DEPLOY);
    String canaryDeleteStepFqn = getExecutionStepFqn(ctx.getCurrentField(), K8S_CANARY_DELETE);
    K8sCanaryDeleteStepParameters canaryDeleteStepParameters =
        (K8sCanaryDeleteStepParameters) ((StepElementParameters) stepParameters).getSpec();
    canaryDeleteStepParameters.setCanaryStepFqn(canaryStepFqn);
    canaryDeleteStepParameters.setCanaryDeleteStepFqn(canaryDeleteStepFqn);

    return stepParameters;
  }

  @Override
  public StepType getStepSpecType(PlanCreationContext ctx, K8sCanaryDeleteStepNode stepElement) {
    if (featureFlagService.isEnabled(
            ctx.getMetadata().getAccountIdentifier(), FeatureName.CDS_K8S_ASYNC_STEP_STRATEGY)) {
      return K8sCanaryDeleteStepV2.STEP_TYPE;
    }
    return K8sCanaryDeleteStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType(PlanCreationContext ctx, K8sCanaryDeleteStepNode stepElement) {
    if (featureFlagService.isEnabled(
            ctx.getMetadata().getAccountIdentifier(), FeatureName.CDS_K8S_ASYNC_STEP_STRATEGY)) {
      return OrchestrationFacilitatorType.ASYNC;
    }
    return OrchestrationFacilitatorType.TASK;
  }
}
