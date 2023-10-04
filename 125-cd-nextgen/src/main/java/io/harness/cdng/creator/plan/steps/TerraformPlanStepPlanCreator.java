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
import io.harness.cdng.provision.terraform.TerraformPlanStep;
import io.harness.cdng.provision.terraform.TerraformPlanStepNode;
import io.harness.cdng.provision.terraform.TerraformPlanStepParameters;
import io.harness.cdng.provision.terraform.TerraformPlanStepV2;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Set;

@OwnedBy(CDP)
public class TerraformPlanStepPlanCreator extends CDPMSStepPlanCreatorV2<TerraformPlanStepNode> {
  @Inject private CDFeatureFlagHelper featureFlagService;

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.TERRAFORM_PLAN);
  }

  @Override
  public Class<TerraformPlanStepNode> getFieldClass() {
    return TerraformPlanStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, TerraformPlanStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, TerraformPlanStepNode stepElement) {
    StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    String stepFqn = YamlUtils.getFullyQualifiedName(ctx.getCurrentField().getNode());
    TerraformPlanStepParameters terraformPlanStepParameters =
        (TerraformPlanStepParameters) ((StepElementParameters) stepParameters).getSpec();
    terraformPlanStepParameters.setStepFqn(stepFqn);

    return stepParameters;
  }

  @Override
  public StepType getStepSpecType(PlanCreationContext ctx, TerraformPlanStepNode stepElement) {
    if (featureFlagService.isEnabled(ctx.getMetadata().getAccountIdentifier(),
            FeatureName.CDS_SUPPORT_EXPRESSION_REMOTE_TERRAFORM_VAR_FILES_NG)) {
      return TerraformPlanStepV2.STEP_TYPE;
    } else {
      return TerraformPlanStep.STEP_TYPE;
    }
  }

  @Override
  public String getFacilitatorType(PlanCreationContext ctx, TerraformPlanStepNode stepElement) {
    if (featureFlagService.isEnabled(ctx.getMetadata().getAccountIdentifier(),
            FeatureName.CDS_SUPPORT_EXPRESSION_REMOTE_TERRAFORM_VAR_FILES_NG)) {
      return OrchestrationFacilitatorType.TASK_CHAIN;
    } else {
      return OrchestrationFacilitatorType.TASK;
    }
  }
}
