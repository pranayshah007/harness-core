/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.aws.asg;

import static io.harness.executions.steps.StepSpecTypeConstants.ASG_BLUE_GREEN_DEPLOY;
import static io.harness.executions.steps.StepSpecTypeConstants.ASG_BLUE_GREEN_ROLLBACK;
import static io.harness.executions.steps.StepSpecTypeConstants.ASG_BLUE_GREEN_SWAP_SERVICE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.asg.AsgBlueGreenRollbackStepNode;
import io.harness.cdng.aws.asg.AsgBlueGreenRollbackStepParameters;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class AsgBlueGreenRollbackStepPlanCreator extends CDPMSStepPlanCreatorV2<AsgBlueGreenRollbackStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(ASG_BLUE_GREEN_ROLLBACK);
  }

  @Override
  public Class<AsgBlueGreenRollbackStepNode> getFieldClass() {
    return AsgBlueGreenRollbackStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, AsgBlueGreenRollbackStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, AsgBlueGreenRollbackStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    AsgBlueGreenRollbackStepParameters asgBlueGreenRollbackStepParameters =
        (AsgBlueGreenRollbackStepParameters) ((StepElementParameters) stepParameters).getSpec();

    String asgBlueGreenDeployFnq = getExecutionStepFqn(ctx.getCurrentField(), ASG_BLUE_GREEN_DEPLOY);
    String asgBlueGreenSwapServiceFnq = getExecutionStepFqn(ctx.getCurrentField(), ASG_BLUE_GREEN_SWAP_SERVICE);

    asgBlueGreenRollbackStepParameters.setAsgBlueGreenDeplyFnq(asgBlueGreenDeployFnq);
    asgBlueGreenRollbackStepParameters.setAsgBlueGreenSwapServiceFnq(asgBlueGreenSwapServiceFnq);
    asgBlueGreenRollbackStepParameters.setDelegateSelectors(
        stepElement.getAsgBlueGreenRollbackStepInfo().getDelegateSelectors());

    return stepParameters;
  }
}
