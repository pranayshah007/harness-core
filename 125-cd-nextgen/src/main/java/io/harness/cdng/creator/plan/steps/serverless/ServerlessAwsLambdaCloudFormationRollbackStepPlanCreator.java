/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.serverless;

import static io.harness.cdng.visitor.YamlTypes.SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK_V2;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaCloudFormationRollbackStepNode;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaCloudFormationRollbackStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class ServerlessAwsLambdaCloudFormationRollbackStepPlanCreator
    extends CDPMSStepPlanCreatorV2<ServerlessAwsLambdaCloudFormationRollbackStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK_V2);
  }

  @Override
  public Class<ServerlessAwsLambdaCloudFormationRollbackStepNode> getFieldClass() {
    return ServerlessAwsLambdaCloudFormationRollbackStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(
      PlanCreationContext ctx, ServerlessAwsLambdaCloudFormationRollbackStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(
      PlanCreationContext ctx, ServerlessAwsLambdaCloudFormationRollbackStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    String serverlessAwsLambdaRollbackFnq =
        getExecutionStepFqn(ctx.getCurrentField(), SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK_V2);
    ((ServerlessAwsLambdaCloudFormationRollbackStepParameters) ((StepElementParameters) stepParameters).getSpec())
        .setServerlessAwsLambdaRollbackFnq(serverlessAwsLambdaRollbackFnq);

    return stepParameters;
  }
}
