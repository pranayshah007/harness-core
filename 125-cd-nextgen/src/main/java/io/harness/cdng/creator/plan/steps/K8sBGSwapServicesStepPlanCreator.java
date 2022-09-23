/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.visitor.YamlTypes.K8S_BG_SWAP_SERVICES;
import static io.harness.cdng.visitor.YamlTypes.K8S_BLUE_GREEN_DEPLOY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.k8s.K8sBGSwapServicesStepNode;
import io.harness.cdng.k8s.K8sBGSwapServicesStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(CDP)
public class K8sBGSwapServicesStepPlanCreator extends CDPMSStepPlanCreatorV2<K8sBGSwapServicesStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.K8S_BG_SWAP_SERVICES);
  }

  @Override
  public Class<K8sBGSwapServicesStepNode> getFieldClass() {
    return K8sBGSwapServicesStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, K8sBGSwapServicesStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, K8sBGSwapServicesStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    String bgStepFqn = getExecutionStepFqn(ctx.getCurrentField(), K8S_BLUE_GREEN_DEPLOY);
    String bgSwapServicesStepFqn = getExecutionStepFqn(ctx.getCurrentField(), K8S_BG_SWAP_SERVICES);
    K8sBGSwapServicesStepParameters k8sBGSwapServicesStepParameters =
        (K8sBGSwapServicesStepParameters) ((StepElementParameters) stepParameters).getSpec();
    k8sBGSwapServicesStepParameters.setBlueGreenStepFqn(bgStepFqn);
    k8sBGSwapServicesStepParameters.setBlueGreenSwapServicesStepFqn(bgSwapServicesStepFqn);

    return stepParameters;
  }
}
