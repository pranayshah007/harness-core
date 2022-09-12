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
import io.harness.cdng.k8s.K8sCanaryDeleteStepNode;
import io.harness.cdng.k8s.K8sCanaryDeleteStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(CDP)
public class K8sCanaryDeleteStepPlanCreator extends CDPMSStepPlanCreatorV2<K8sCanaryDeleteStepNode> {
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
}
