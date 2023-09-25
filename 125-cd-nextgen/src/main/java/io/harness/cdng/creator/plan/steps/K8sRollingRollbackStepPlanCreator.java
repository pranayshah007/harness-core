/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.visitor.YamlTypes.K8S_ROLLING_DEPLOY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.K8sRollingRollbackStep;
import io.harness.cdng.k8s.K8sRollingRollbackStepNode;
import io.harness.cdng.k8s.K8sRollingRollbackStepParameters;
import io.harness.cdng.k8s.K8sRollingRollbackStepV2;
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
public class K8sRollingRollbackStepPlanCreator extends CDPMSStepPlanCreatorV2<K8sRollingRollbackStepNode> {
  @Inject private CDFeatureFlagHelper featureFlagService;

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.K8S_ROLLING_ROLLBACK);
  }

  @Override
  public Class<K8sRollingRollbackStepNode> getFieldClass() {
    return K8sRollingRollbackStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, K8sRollingRollbackStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, K8sRollingRollbackStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    String rollingFqn = getExecutionStepFqn(ctx.getCurrentField(), K8S_ROLLING_DEPLOY);
    ((K8sRollingRollbackStepParameters) ((StepElementParameters) stepParameters).getSpec())
        .setRollingStepFqn(rollingFqn);

    return stepParameters;
  }

  @Override
  public StepType getStepSpecType(PlanCreationContext ctx, K8sRollingRollbackStepNode stepElement) {
    if (featureFlagService.isEnabled(
            ctx.getMetadata().getAccountIdentifier(), FeatureName.CDS_K8S_ASYNC_STEP_STRATEGY)) {
      return K8sRollingRollbackStepV2.STEP_TYPE;
    } else {
      return K8sRollingRollbackStep.STEP_TYPE;
    }
  }

  @Override
  public String getFacilitatorType(PlanCreationContext ctx, K8sRollingRollbackStepNode stepElement) {
    if (featureFlagService.isEnabled(
            ctx.getMetadata().getAccountIdentifier(), FeatureName.CDS_K8S_ASYNC_STEP_STRATEGY)) {
      return OrchestrationFacilitatorType.ASYNC;
    } else {
      return OrchestrationFacilitatorType.TASK;
    }
  }
}
