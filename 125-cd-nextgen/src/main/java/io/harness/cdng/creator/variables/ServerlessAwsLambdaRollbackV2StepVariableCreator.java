/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaRollbackV2StepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Collections;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
public class ServerlessAwsLambdaRollbackV2StepVariableCreator
    extends GenericStepVariableCreator<ServerlessAwsLambdaRollbackV2StepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK_V2);
  }

  @Override
  public Class<ServerlessAwsLambdaRollbackV2StepNode> getFieldClass() {
    return ServerlessAwsLambdaRollbackV2StepNode.class;
  }
}
