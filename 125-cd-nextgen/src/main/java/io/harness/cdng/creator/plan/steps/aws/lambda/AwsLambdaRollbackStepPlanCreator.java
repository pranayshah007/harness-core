/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.aws.lambda;

import static io.harness.cdng.visitor.YamlTypes.AWS_LAMBDA_DEPLOY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.lambda.rollback.AwsLambdaRollbackStepNode;
import io.harness.cdng.aws.lambda.rollback.AwsLambdaRollbackStepParameters;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class AwsLambdaRollbackStepPlanCreator extends CDPMSStepPlanCreatorV2<AwsLambdaRollbackStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.AWS_LAMBDA_ROLLBACK);
  }

  @Override
  public Class<AwsLambdaRollbackStepNode> getFieldClass() {
    return AwsLambdaRollbackStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, AwsLambdaRollbackStepNode stepNode) {
    return super.createPlanForField(ctx, stepNode);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, AwsLambdaRollbackStepNode stepNode) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepNode);

    String awsLambdaDeployStepFqn = getExecutionStepFqn(ctx.getCurrentField(), AWS_LAMBDA_DEPLOY);
    AwsLambdaRollbackStepParameters awsLambdaRollbackStepParameters =
        (AwsLambdaRollbackStepParameters) ((StepElementParameters) stepParameters).getSpec();
    awsLambdaRollbackStepParameters.setDelegateSelectors(
        stepNode.getAwsLambdaRollbackStepInfo().getDelegateSelectors());
    awsLambdaRollbackStepParameters.setAwsLambdaDeployStepFqn(awsLambdaDeployStepFqn);
    return stepParameters;
  }
}
