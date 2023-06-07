/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.serverless;

import static io.harness.cdng.visitor.YamlTypes.DOWNLOAD_SERVERLESS_MANIFESTS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaPrepareRollbackContainerStepNode;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaPrepareRollbackContainerStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class ServerlessAwsLambdaPrepareRollbackContainerStepPlanCreator
    extends CDPMSStepPlanCreatorV2<ServerlessAwsLambdaPrepareRollbackContainerStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.SERVERLESS_PREPARE_ROLLBACK);
  }

  @Override
  public Class<ServerlessAwsLambdaPrepareRollbackContainerStepNode> getFieldClass() {
    return ServerlessAwsLambdaPrepareRollbackContainerStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(
      PlanCreationContext ctx, ServerlessAwsLambdaPrepareRollbackContainerStepNode stepNode) {
    return super.createPlanForField(ctx, stepNode);
  }

  @Override
  protected StepParameters getStepParameters(
      PlanCreationContext ctx, ServerlessAwsLambdaPrepareRollbackContainerStepNode stepNode) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepNode);
    String downloadManifestsFqn = getExecutionStepFqn(ctx.getCurrentField(), DOWNLOAD_SERVERLESS_MANIFESTS);
    ServerlessAwsLambdaPrepareRollbackContainerStepParameters
        serverlessAwsLambdaPrepareRollbackContainerStepParameters =
            (ServerlessAwsLambdaPrepareRollbackContainerStepParameters) ((StepElementParameters) stepParameters)
                .getSpec();
    serverlessAwsLambdaPrepareRollbackContainerStepParameters.setDownloadManifestsFqn(downloadManifestsFqn);
    serverlessAwsLambdaPrepareRollbackContainerStepParameters.setDelegateSelectors(
        stepNode.getServerlessAwsLambdaPrepareRollbackContainerStepInfo().getDelegateSelectors());
    return stepParameters;
  }
}
