/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.executions.steps.StepSpecTypeConstants.TAS_ROLLING_DEPLOY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.tas.TasRollingDeployStepNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class TasRollingDeployStepPlanCreator extends CDPMSStepPlanCreatorV2<TasRollingDeployStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(TAS_ROLLING_DEPLOY);
  }

  @Override
  public Class<TasRollingDeployStepNode> getFieldClass() {
    return TasRollingDeployStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, TasRollingDeployStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, TasRollingDeployStepNode stepElement) {
    return super.getStepParameters(ctx, stepElement);
  }
}
