/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.azure.webapp;

import static io.harness.cdng.visitor.YamlTypes.AZURE_SLOT_DEPLOYMENT;
import static io.harness.cdng.visitor.YamlTypes.AZURE_SWAP_SLOT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.azure.webapp.AzureWebAppRollbackStepNode;
import io.harness.cdng.azure.webapp.AzureWebAppRollbackStepParameters;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class AzureWebAppRollbackStepPlanCreator extends CDPMSStepPlanCreatorV2<AzureWebAppRollbackStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.AZURE_WEBAPP_ROLLBACK);
  }

  @Override
  public Class<AzureWebAppRollbackStepNode> getFieldClass() {
    return AzureWebAppRollbackStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, AzureWebAppRollbackStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, AzureWebAppRollbackStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);
    AzureWebAppRollbackStepParameters azureWebAppRollbackStepParameters =
        (AzureWebAppRollbackStepParameters) ((StepElementParameters) stepParameters).getSpec();
    String slotDeploymentStepFqn = getExecutionStepFqn(ctx.getCurrentField(), AZURE_SLOT_DEPLOYMENT);
    azureWebAppRollbackStepParameters.setSlotDeploymentStepFqn(slotDeploymentStepFqn);
    String swapSlotStepFqn = getExecutionStepFqn(ctx.getCurrentField(), AZURE_SWAP_SLOT);
    azureWebAppRollbackStepParameters.setSwapSlotStepFqn(swapSlotStepFqn);

    return stepParameters;
  }
}
