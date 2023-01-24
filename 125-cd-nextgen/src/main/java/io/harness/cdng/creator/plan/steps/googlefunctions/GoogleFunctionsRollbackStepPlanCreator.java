/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.googlefunctions;

import static io.harness.cdng.visitor.YamlTypes.GOOGLE_CLOUD_FUNCTIONS_DEPLOY;
import static io.harness.cdng.visitor.YamlTypes.GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.googlefunctions.rollback.GoogleFunctionsRollbackStepNode;
import io.harness.cdng.googlefunctions.rollback.GoogleFunctionsRollbackStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class GoogleFunctionsRollbackStepPlanCreator extends CDPMSStepPlanCreatorV2<GoogleFunctionsRollbackStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_ROLLBACK);
  }

  @Override
  public Class<GoogleFunctionsRollbackStepNode> getFieldClass() {
    return GoogleFunctionsRollbackStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, GoogleFunctionsRollbackStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, GoogleFunctionsRollbackStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);
    String googleFunctionDeployWithoutTrafficStepFnq =
        getExecutionStepFqn(ctx.getCurrentField(), GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC);
    String googleFunctionDeployStepFnq = getExecutionStepFqn(ctx.getCurrentField(), GOOGLE_CLOUD_FUNCTIONS_DEPLOY);

    GoogleFunctionsRollbackStepParameters googleFunctionsRollbackStepParameters =
        (GoogleFunctionsRollbackStepParameters) ((StepElementParameters) stepParameters).getSpec();
    googleFunctionsRollbackStepParameters.setDelegateSelectors(
        stepElement.getGoogleFunctionsRollbackStepInfo().getDelegateSelectors());
    googleFunctionsRollbackStepParameters.setGoogleFunctionDeployWithoutTrafficStepFnq(
        googleFunctionDeployWithoutTrafficStepFnq);
    googleFunctionsRollbackStepParameters.setGoogleFunctionDeployStepFnq(googleFunctionDeployStepFnq);

    return stepParameters;
  }
}
