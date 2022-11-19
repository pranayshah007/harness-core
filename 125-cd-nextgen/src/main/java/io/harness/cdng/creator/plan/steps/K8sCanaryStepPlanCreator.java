/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import io.harness.cdng.k8s.K8sCanaryStepNode;
import io.harness.cdng.commons.StepSpecTypeConstants;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import com.google.common.collect.Sets;
import java.util.Set;

public class K8sCanaryStepPlanCreator extends CDPMSStepPlanCreatorV2<K8sCanaryStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.K8S_CANARY_DEPLOY);
  }

  @Override
  public Class<K8sCanaryStepNode> getFieldClass() {
    return K8sCanaryStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, K8sCanaryStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }
}
