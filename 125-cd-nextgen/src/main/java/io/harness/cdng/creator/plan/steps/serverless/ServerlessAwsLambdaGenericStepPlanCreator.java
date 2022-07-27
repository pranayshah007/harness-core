/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.serverless;

import com.google.common.collect.Sets;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.serverless.ServerlessAwsLambdaGenericStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class ServerlessAwsLambdaGenericStepPlanCreator
    extends CDPMSStepPlanCreatorV2<ServerlessAwsLambdaGenericStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_GENERIC);
  }

  @Override
  public Class<ServerlessAwsLambdaGenericStepNode> getFieldClass() {
    return ServerlessAwsLambdaGenericStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(
      PlanCreationContext ctx, ServerlessAwsLambdaGenericStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }
}
