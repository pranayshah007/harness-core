/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.provision.terraform.TerraformDestroyStep;
import io.harness.cdng.provision.terraform.TerraformDestroyStepNode;
import io.harness.cdng.provision.terraform.TerraformDestroyStepV2;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Set;

@OwnedBy(CDP)
public class TerraformDestroyStepPlanCreator extends CDPMSStepPlanCreatorV2<TerraformDestroyStepNode> {
  @Inject private CDFeatureFlagHelper featureFlagService;

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.TERRAFORM_DESTROY);
  }

  @Override
  public Class<TerraformDestroyStepNode> getFieldClass() {
    return TerraformDestroyStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, TerraformDestroyStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  public StepType getStepSpecType(PlanCreationContext ctx, TerraformDestroyStepNode stepElement) {
    if (featureFlagService.isEnabled(ctx.getMetadata().getAccountIdentifier(),
            FeatureName.CDS_SUPPORT_EXPRESSION_REMOTE_TERRAFORM_VAR_FILES_NG)) {
      return TerraformDestroyStepV2.STEP_TYPE;
    } else {
      return TerraformDestroyStep.STEP_TYPE;
    }
  }

  @Override
  public String getFacilitatorType(PlanCreationContext ctx, TerraformDestroyStepNode stepElement) {
    if (featureFlagService.isEnabled(ctx.getMetadata().getAccountIdentifier(),
            FeatureName.CDS_SUPPORT_EXPRESSION_REMOTE_TERRAFORM_VAR_FILES_NG)) {
      return OrchestrationFacilitatorType.TASK_CHAIN;
    } else {
      return OrchestrationFacilitatorType.TASK;
    }
  }
}
