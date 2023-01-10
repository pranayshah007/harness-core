/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.executions.steps.StepSpecTypeConstants.TAS_CANARY_APP_SETUP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.tas.TasCanaryAppSetupStepNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class TasCanaryAppSetupStepPlanCreator extends CDPMSStepPlanCreatorV2<TasCanaryAppSetupStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(TAS_CANARY_APP_SETUP);
  }

  @Override
  public Class<TasCanaryAppSetupStepNode> getFieldClass() {
    return TasCanaryAppSetupStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, TasCanaryAppSetupStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, TasCanaryAppSetupStepNode stepElement) {
    return super.getStepParameters(ctx, stepElement);
  }
}
