/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.aws.asg;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.asg.AsgCanaryDeployStepNode;
import io.harness.cdng.aws.asg.AsgCanaryDeployStepParameters;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class AsgCanaryDeployStepPlanCreator extends CDPMSStepPlanCreatorV2<AsgCanaryDeployStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.ASG_CANARY_DEPLOY);
  }

  @Override
  public Class<AsgCanaryDeployStepNode> getFieldClass() {
    return AsgCanaryDeployStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, AsgCanaryDeployStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, AsgCanaryDeployStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);
    AsgCanaryDeployStepParameters asgCanaryDeployStepParameters =
        (AsgCanaryDeployStepParameters) ((StepElementParameters) stepParameters).getSpec();
    asgCanaryDeployStepParameters.setDelegateSelectors(stepElement.getAsgCanaryDeployStepInfo().getDelegateSelectors());
    asgCanaryDeployStepParameters.setAsgName(stepElement.getAsgCanaryDeployStepInfo().getAsgName());

    return stepParameters;
  }
}
