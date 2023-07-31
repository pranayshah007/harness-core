/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.serverless;
import static io.harness.cdng.visitor.YamlTypes.SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK_V2;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaRollbackV2StepNode;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaRollbackV2StepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
public class ServerlessAwsLambdaRollbackV2StepPlanCreator
    extends CDPMSStepPlanCreatorV2<ServerlessAwsLambdaRollbackV2StepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK_V2);
  }

  @Override
  public Class<ServerlessAwsLambdaRollbackV2StepNode> getFieldClass() {
    return ServerlessAwsLambdaRollbackV2StepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(
      PlanCreationContext ctx, ServerlessAwsLambdaRollbackV2StepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(
      PlanCreationContext ctx, ServerlessAwsLambdaRollbackV2StepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    String serverlessAwsLambdaRollbackFnq =
        getExecutionStepFqn(ctx.getCurrentField(), SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK_V2);
    ((ServerlessAwsLambdaRollbackV2StepParameters) ((StepElementParameters) stepParameters).getSpec())
        .setServerlessAwsLambdaRollbackFnq(serverlessAwsLambdaRollbackFnq);

    return stepParameters;
  }
}
