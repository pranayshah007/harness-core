/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.visitor.YamlTypes.K8S_CANARY_DEPLOY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.k8s.K8sDryRunManifestStepNode;
import io.harness.cdng.k8s.K8sRollingStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(CDP)
public class K8sDryRunManifestStepPlanCreator extends CDPMSStepPlanCreatorV2<K8sDryRunManifestStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.K8S_DRY_RUN_MANIFEST);
  }

  @Override
  public Class<K8sDryRunManifestStepNode> getFieldClass() {
    return K8sDryRunManifestStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, K8sDryRunManifestStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, K8sDryRunManifestStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    String canaryStepFqn = getExecutionStepFqn(ctx.getCurrentField(), K8S_CANARY_DEPLOY);
    ((K8sRollingStepParameters) ((StepElementParameters) stepParameters).getSpec()).setCanaryStepFqn(canaryStepFqn);

    return stepParameters;
  }
}
