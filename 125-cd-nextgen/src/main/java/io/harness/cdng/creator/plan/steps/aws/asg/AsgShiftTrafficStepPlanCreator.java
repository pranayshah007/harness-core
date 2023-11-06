/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.aws.asg;

import static io.harness.cdng.visitor.YamlTypes.ASG_BLUE_GREEN_DEPLOY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.aws.asg.AsgShiftTrafficStepNode;
import io.harness.cdng.aws.asg.AsgShiftTrafficStepParameters;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_AMI_ASG})
@OwnedBy(HarnessTeam.CDP)
public class AsgShiftTrafficStepPlanCreator extends CDPMSStepPlanCreatorV2<AsgShiftTrafficStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.ASG_SHIFT_TRAFFIC);
  }

  @Override
  public Class<AsgShiftTrafficStepNode> getFieldClass() {
    return AsgShiftTrafficStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, AsgShiftTrafficStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, AsgShiftTrafficStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    String asgBlueGreenDeployFnq = getExecutionStepFqn(ctx.getCurrentField(), ASG_BLUE_GREEN_DEPLOY);
    AsgShiftTrafficStepParameters asgShiftTrafficStepParameters =
        (AsgShiftTrafficStepParameters) ((StepElementParameters) stepParameters).getSpec();
    asgShiftTrafficStepParameters.setDelegateSelectors(stepElement.getAsgShiftTrafficStepInfo().getDelegateSelectors());
    asgShiftTrafficStepParameters.setWeight(stepElement.getAsgShiftTrafficStepInfo().getWeight());
    asgShiftTrafficStepParameters.setDownsizeOldAsg(stepElement.getAsgShiftTrafficStepInfo().getDownsizeOldAsg());
    asgShiftTrafficStepParameters.setAsgBlueGreenDeployFqn(asgBlueGreenDeployFnq);

    return stepParameters;
  }
}
