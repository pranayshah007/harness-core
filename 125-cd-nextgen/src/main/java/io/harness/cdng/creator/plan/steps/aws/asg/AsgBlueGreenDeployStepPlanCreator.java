/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.aws.asg;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.asg.AsgBlueGreenDeployStepNode;
import io.harness.cdng.aws.asg.AsgBlueGreenDeployStepParameters;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class AsgBlueGreenDeployStepPlanCreator extends CDPMSStepPlanCreatorV2<AsgBlueGreenDeployStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.ASG_BLUE_GREEN_DEPLOY);
  }

  @Override
  public Class<AsgBlueGreenDeployStepNode> getFieldClass() {
    return AsgBlueGreenDeployStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, AsgBlueGreenDeployStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, AsgBlueGreenDeployStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);
    AsgBlueGreenDeployStepParameters asgBlueGreenDeployStepParameters =
        (AsgBlueGreenDeployStepParameters) ((StepElementParameters) stepParameters).getSpec();
    asgBlueGreenDeployStepParameters.setLoadBalancer(stepElement.getAsgBlueGreenDeployStepInfo().getLoadBalancer());
    asgBlueGreenDeployStepParameters.setProdListener(stepElement.getAsgBlueGreenDeployStepInfo().getProdListener());
    asgBlueGreenDeployStepParameters.setProdListenerRuleArn(
        stepElement.getAsgBlueGreenDeployStepInfo().getProdListenerRuleArn());
    asgBlueGreenDeployStepParameters.setStageListener(stepElement.getAsgBlueGreenDeployStepInfo().getStageListener());
    asgBlueGreenDeployStepParameters.setStageListenerRuleArn(
        stepElement.getAsgBlueGreenDeployStepInfo().getStageListenerRuleArn());
    asgBlueGreenDeployStepParameters.setDelegateSelectors(
        stepElement.getAsgBlueGreenDeployStepInfo().getDelegateSelectors());
    asgBlueGreenDeployStepParameters.setUseAlreadyRunningInstances(
        stepElement.getAsgBlueGreenDeployStepInfo().getUseAlreadyRunningInstances());

    return stepParameters;
  }
}
