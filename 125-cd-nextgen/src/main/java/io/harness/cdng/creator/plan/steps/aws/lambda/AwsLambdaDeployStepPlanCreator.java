/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.aws.lambda;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.lambda.deploy.AwsLambdaDeployStepNode;
import io.harness.cdng.aws.lambda.deploy.AwsLambdaDeployStepParameters;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class AwsLambdaDeployStepPlanCreator extends CDPMSStepPlanCreatorV2<AwsLambdaDeployStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.AWS_LAMBDA_DEPLOY);
  }

  @Override
  public Class<AwsLambdaDeployStepNode> getFieldClass() {
    return AwsLambdaDeployStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, AwsLambdaDeployStepNode stepNode) {
    return super.createPlanForField(ctx, stepNode);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, AwsLambdaDeployStepNode stepNode) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepNode);
    AwsLambdaDeployStepParameters lambdaDeployStepParameters =
        (AwsLambdaDeployStepParameters) ((StepElementParameters) stepParameters).getSpec();
    lambdaDeployStepParameters.setDelegateSelectors(stepNode.getLambdaDeployStepInfo().getDelegateSelectors());
    return stepParameters;
  }
}
