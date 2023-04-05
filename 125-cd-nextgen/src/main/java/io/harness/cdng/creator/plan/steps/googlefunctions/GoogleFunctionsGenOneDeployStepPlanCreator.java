/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.googlefunctions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.googlefunctions.deploygenone.GoogleFunctionsGenOneDeployStepNode;
import io.harness.cdng.googlefunctions.deploygenone.GoogleFunctionsGenOneDeployStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class GoogleFunctionsGenOneDeployStepPlanCreator
    extends CDPMSStepPlanCreatorV2<GoogleFunctionsGenOneDeployStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_GEN_ONE_DEPLOY);
  }

  @Override
  public Class<GoogleFunctionsGenOneDeployStepNode> getFieldClass() {
    return GoogleFunctionsGenOneDeployStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(
      PlanCreationContext ctx, GoogleFunctionsGenOneDeployStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, GoogleFunctionsGenOneDeployStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);
    GoogleFunctionsGenOneDeployStepParameters googleFunctionsGenOneDeployStepParameters =
        (GoogleFunctionsGenOneDeployStepParameters) ((StepElementParameters) stepParameters).getSpec();
    googleFunctionsGenOneDeployStepParameters.setDelegateSelectors(
        stepElement.getGoogleFunctionsGenOneDeployStepInfo().getDelegateSelectors());
    googleFunctionsGenOneDeployStepParameters.setUpdateFieldMask(
        stepElement.getGoogleFunctionsGenOneDeployStepInfo().getUpdateFieldMask());
    return stepParameters;
  }
}
