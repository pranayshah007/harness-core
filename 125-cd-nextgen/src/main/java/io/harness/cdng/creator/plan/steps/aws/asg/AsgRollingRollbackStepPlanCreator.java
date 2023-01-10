/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.aws.asg;

import static io.harness.executions.steps.StepSpecTypeConstants.ASG_ROLLING_DEPLOY;
import static io.harness.executions.steps.StepSpecTypeConstants.ASG_ROLLING_ROLLBACK;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.asg.AsgRollingRollbackStepNode;
import io.harness.cdng.aws.asg.AsgRollingRollbackStepParameters;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class AsgRollingRollbackStepPlanCreator extends CDPMSStepPlanCreatorV2<AsgRollingRollbackStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(ASG_ROLLING_ROLLBACK);
  }

  @Override
  public Class<AsgRollingRollbackStepNode> getFieldClass() {
    return AsgRollingRollbackStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, AsgRollingRollbackStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, AsgRollingRollbackStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    String asgDeployFqn = getExecutionStepFqn(ctx.getCurrentField(), ASG_ROLLING_DEPLOY);
    AsgRollingRollbackStepParameters asgRollingRollbackStepParameters =
        (AsgRollingRollbackStepParameters) ((StepElementParameters) stepParameters).getSpec();
    asgRollingRollbackStepParameters.setAsgRollingDeployFqn(asgDeployFqn);
    asgRollingRollbackStepParameters.setDelegateSelectors(
        stepElement.getAsgRollingRollbackStepInfo().getDelegateSelectors());
    return stepParameters;
  }
}
