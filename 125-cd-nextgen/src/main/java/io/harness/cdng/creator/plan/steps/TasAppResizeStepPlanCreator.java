/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.cdng.visitor.YamlTypes.TAS_BASIC_APP_SETUP;
import static io.harness.cdng.visitor.YamlTypes.TAS_CANARY_APP_SETUP;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_APP_RESIZE;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_BG_APP_SETUP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.tas.TasAppResizeStepNode;
import io.harness.cdng.tas.TasAppResizeStepParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class TasAppResizeStepPlanCreator extends CDPMSStepPlanCreatorV2<TasAppResizeStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(TAS_APP_RESIZE);
  }

  @Override
  public Class<TasAppResizeStepNode> getFieldClass() {
    return TasAppResizeStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, TasAppResizeStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, TasAppResizeStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);
    String tasBGSetupFqn = getExecutionStepFqn(ctx.getCurrentField(), TAS_BG_APP_SETUP);
    TasAppResizeStepParameters tasAppResizeStepParameters =
        (TasAppResizeStepParameters) ((StepElementParameters) stepParameters).getSpec();
    tasAppResizeStepParameters.setTasBGSetupFqn(tasBGSetupFqn);

    String tasBasicSetupFqn = getExecutionStepFqn(ctx.getCurrentField(), TAS_BASIC_APP_SETUP);
    tasAppResizeStepParameters.setTasBasicSetupFqn(tasBasicSetupFqn);

    String tasCanarySetupFqn = getExecutionStepFqn(ctx.getCurrentField(), TAS_CANARY_APP_SETUP);
    tasAppResizeStepParameters.setTasCanarySetupFqn(tasCanarySetupFqn);
    return stepParameters;
  }
}