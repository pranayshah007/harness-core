/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.executions.steps.StepSpecTypeConstants.TAS_APP_RESIZE;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_BASIC_APP_SETUP;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_BG_APP_SETUP;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_CANARY_APP_SETUP;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_ROLLBACK;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.tas.TasRollbackStepNode;
import io.harness.cdng.tas.TasRollbackStepParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class TasRollbackStepPlanCreator extends CDPMSStepPlanCreatorV2<TasRollbackStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(TAS_ROLLBACK);
  }

  @Override
  public Class<TasRollbackStepNode> getFieldClass() {
    return TasRollbackStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, TasRollbackStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, TasRollbackStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);
    String tasBGSetupFqn = getExecutionStepFqn(ctx.getCurrentField(), TAS_BG_APP_SETUP);
    TasRollbackStepParameters tasRollbackStepParameters =
        (TasRollbackStepParameters) ((StepElementParameters) stepParameters).getSpec();
    tasRollbackStepParameters.setTasBGSetupFqn(tasBGSetupFqn);
    String tasBasicSetupFqn = getExecutionStepFqn(ctx.getCurrentField(), TAS_BASIC_APP_SETUP);
    tasRollbackStepParameters.setTasBasicSetupFqn(tasBasicSetupFqn);
    String tasCanarySetupFqn = getExecutionStepFqn(ctx.getCurrentField(), TAS_CANARY_APP_SETUP);
    tasRollbackStepParameters.setTasCanarySetupFqn(tasCanarySetupFqn);
    String tasResizeFqn = getExecutionStepFqn(ctx.getCurrentField(), TAS_APP_RESIZE);
    tasRollbackStepParameters.setTasResizeFqn(tasResizeFqn);
    return stepParameters;
  }
}