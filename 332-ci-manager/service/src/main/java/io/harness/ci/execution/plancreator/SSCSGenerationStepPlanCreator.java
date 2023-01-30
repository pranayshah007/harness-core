/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plancreator;

import com.google.common.collect.Sets;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.SSCSGenerationStepNode;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import java.util.Set;

public class SSCSGenerationStepPlanCreator extends CIPMSStepPlanCreatorV2<SSCSGenerationStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(CIStepInfoType.SSCSGeneration.getDisplayName());
  }

  @Override
  public Class<SSCSGenerationStepNode> getFieldClass() {
    return SSCSGenerationStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, SSCSGenerationStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }
}
