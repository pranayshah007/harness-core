/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.aws.asg;

import static io.harness.cdng.visitor.YamlTypes.ASG_BLUE_GREEN_DEPLOY;
import static io.harness.cdng.visitor.YamlTypes.ASG_BLUE_GREEN_SWAP_SERVICE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.asg.AsgBlueGreenSwapServiceStepNode;
import io.harness.cdng.aws.asg.AsgBlueGreenSwapServiceStepParameters;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class AsgBlueGreenSwapServiceStepPlanCreator extends CDPMSStepPlanCreatorV2<AsgBlueGreenSwapServiceStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.ASG_BLUE_GREEN_SWAP_SERVICE);
  }

  @Override
  public Class<AsgBlueGreenSwapServiceStepNode> getFieldClass() {
    return AsgBlueGreenSwapServiceStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, AsgBlueGreenSwapServiceStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, AsgBlueGreenSwapServiceStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    String asgBlueGreenDeployFnq = getExecutionStepFqn(ctx.getCurrentField(), ASG_BLUE_GREEN_DEPLOY);
    String asgBlueGreenSwapServiceFnq = getExecutionStepFqn(ctx.getCurrentField(), ASG_BLUE_GREEN_SWAP_SERVICE);
    AsgBlueGreenSwapServiceStepParameters asgBlueGreenSwapServiceStepParameters =
        (AsgBlueGreenSwapServiceStepParameters) ((StepElementParameters) stepParameters).getSpec();
    asgBlueGreenSwapServiceStepParameters.setAsgBlueGreenDeployFqn(asgBlueGreenDeployFnq);
    asgBlueGreenSwapServiceStepParameters.setAsgBlueGreenSwapServiceFqn(asgBlueGreenSwapServiceFnq);

    return stepParameters;
  }
}
