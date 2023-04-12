/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.googlefunctions;

import static io.harness.cdng.visitor.YamlTypes.GOOGLE_CLOUD_FUNCTIONS_GEN_ONE_DEPLOY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.googlefunctions.rollbackgenone.GoogleFunctionsGenOneRollbackStepNode;
import io.harness.cdng.googlefunctions.rollbackgenone.GoogleFunctionsGenOneRollbackStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class GoogleFunctionsGenOneRollbackStepPlanCreator
    extends CDPMSStepPlanCreatorV2<GoogleFunctionsGenOneRollbackStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_GEN_ONE_ROLLBACK);
  }

  @Override
  public Class<GoogleFunctionsGenOneRollbackStepNode> getFieldClass() {
    return GoogleFunctionsGenOneRollbackStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(
      PlanCreationContext ctx, GoogleFunctionsGenOneRollbackStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(
      PlanCreationContext ctx, GoogleFunctionsGenOneRollbackStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);
    String googleFunctionGenOneDeployStepFnq =
        getExecutionStepFqn(ctx.getCurrentField(), GOOGLE_CLOUD_FUNCTIONS_GEN_ONE_DEPLOY);

    GoogleFunctionsGenOneRollbackStepParameters googleFunctionsGenOneRollbackStepParameters =
        (GoogleFunctionsGenOneRollbackStepParameters) ((StepElementParameters) stepParameters).getSpec();
    googleFunctionsGenOneRollbackStepParameters.setDelegateSelectors(
        stepElement.getGoogleFunctionsGenOneRollbackStepInfo().getDelegateSelectors());
    googleFunctionsGenOneRollbackStepParameters.setGoogleFunctionGenOneDeployStepFnq(googleFunctionGenOneDeployStepFnq);

    return stepParameters;
  }
}
