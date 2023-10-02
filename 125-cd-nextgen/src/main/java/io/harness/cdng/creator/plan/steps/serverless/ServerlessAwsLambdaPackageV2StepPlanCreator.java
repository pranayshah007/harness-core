/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.serverless;

import static io.harness.cdng.visitor.YamlTypes.DOWNLOAD_MANIFESTS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaPackageV2StepNode;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaPackageV2StepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_SERVERLESS})
@OwnedBy(HarnessTeam.CDP)
public class ServerlessAwsLambdaPackageV2StepPlanCreator
    extends CDPMSStepPlanCreatorV2<ServerlessAwsLambdaPackageV2StepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_PACKAGE_V2);
  }

  @Override
  public Class<ServerlessAwsLambdaPackageV2StepNode> getFieldClass() {
    return ServerlessAwsLambdaPackageV2StepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(
      PlanCreationContext ctx, ServerlessAwsLambdaPackageV2StepNode stepNode) {
    return super.createPlanForField(ctx, stepNode);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, ServerlessAwsLambdaPackageV2StepNode stepNode) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepNode);
    ServerlessAwsLambdaPackageV2StepParameters serverlessAwsLambdaPackageV2StepParameters =
        (ServerlessAwsLambdaPackageV2StepParameters) ((StepElementParameters) stepParameters).getSpec();
    String downloadManifestsFqn = getExecutionStepFqn(ctx.getCurrentField(), DOWNLOAD_MANIFESTS);
    serverlessAwsLambdaPackageV2StepParameters.setDownloadManifestsFqn(downloadManifestsFqn);
    serverlessAwsLambdaPackageV2StepParameters.setDelegateSelectors(
        stepNode.getServerlessAwsLambdaPackageV2StepInfo().getDelegateSelectors());
    return stepParameters;
  }
}
