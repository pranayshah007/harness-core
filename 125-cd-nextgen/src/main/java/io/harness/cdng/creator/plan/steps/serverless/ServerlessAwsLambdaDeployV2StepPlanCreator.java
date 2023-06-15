/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.serverless;

import static io.harness.cdng.visitor.YamlTypes.DOWNLOAD_SERVERLESS_MANIFESTS;
import static io.harness.cdng.visitor.YamlTypes.SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK_V2;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaDeployStepV2Node;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaDeployStepV2Parameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class ServerlessAwsLambdaDeployV2StepPlanCreator
    extends CDPMSStepPlanCreatorV2<ServerlessAwsLambdaDeployStepV2Node> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_DEPLOY_V2);
  }

  @Override
  public Class<ServerlessAwsLambdaDeployStepV2Node> getFieldClass() {
    return ServerlessAwsLambdaDeployStepV2Node.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(
      PlanCreationContext ctx, ServerlessAwsLambdaDeployStepV2Node stepNode) {
    return super.createPlanForField(ctx, stepNode);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, ServerlessAwsLambdaDeployStepV2Node stepNode) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepNode);
    String packageStepFqn = getExecutionStepFqn(ctx.getCurrentField(), SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK_V2);
    ServerlessAwsLambdaDeployStepV2Parameters serverlessAwsLambdaDeployStepV2Parameters =
        (ServerlessAwsLambdaDeployStepV2Parameters) ((StepElementParameters) stepParameters).getSpec();
    serverlessAwsLambdaDeployStepV2Parameters.setPackageStepFqn(packageStepFqn);
    serverlessAwsLambdaDeployStepV2Parameters.setDelegateSelectors(
        stepNode.getServerlessAwsLambdaDeployStepV2Info().getDelegateSelectors());
    return stepParameters;
  }
}
