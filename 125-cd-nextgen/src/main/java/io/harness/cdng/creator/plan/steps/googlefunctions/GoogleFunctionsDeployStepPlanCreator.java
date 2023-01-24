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
import io.harness.cdng.googlefunctions.deploy.GoogleFunctionsDeployStepNode;
import io.harness.cdng.googlefunctions.deploy.GoogleFunctionsDeployStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class GoogleFunctionsDeployStepPlanCreator extends CDPMSStepPlanCreatorV2<GoogleFunctionsDeployStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_DEPLOY);
  }

  @Override
  public Class<GoogleFunctionsDeployStepNode> getFieldClass() {
    return GoogleFunctionsDeployStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, GoogleFunctionsDeployStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, GoogleFunctionsDeployStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);
    GoogleFunctionsDeployStepParameters googleFunctionsDeployStepParameters =
        (GoogleFunctionsDeployStepParameters) ((StepElementParameters) stepParameters).getSpec();
    googleFunctionsDeployStepParameters.setDelegateSelectors(
        stepElement.getGoogleFunctionsDeployStepInfo().getDelegateSelectors());
    googleFunctionsDeployStepParameters.setUpdateFieldMask(
        stepElement.getGoogleFunctionsDeployStepInfo().getUpdateFieldMask());
    return stepParameters;
  }
}
