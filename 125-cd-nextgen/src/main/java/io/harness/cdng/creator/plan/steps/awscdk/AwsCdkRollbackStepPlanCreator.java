/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.awscdk;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.provision.awscdk.AwsCdkRollbackStepNode;
import io.harness.cdng.provision.awscdk.AwsCdkRollbackStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class AwsCdkRollbackStepPlanCreator extends CDPMSStepPlanCreatorV2<AwsCdkRollbackStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.AWS_CDK_ROLLBACK);
  }

  @Override
  public Class<AwsCdkRollbackStepNode> getFieldClass() {
    return AwsCdkRollbackStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, AwsCdkRollbackStepNode stepNode) {
    return super.createPlanForField(ctx, stepNode);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, AwsCdkRollbackStepNode stepNode) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepNode);
    AwsCdkRollbackStepParameters awsCdkRollbackStepParameters =
        (AwsCdkRollbackStepParameters) ((StepElementParameters) stepParameters).getSpec();
    awsCdkRollbackStepParameters.setDelegateSelectors(stepNode.getAwsCdkRollbackStepInfo().getDelegateSelectors());
    return stepParameters;
  }
}
