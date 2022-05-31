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
import io.harness.cdng.provision.terraform.TerraformRollbackStepNode;
import io.harness.cdng.provision.terraform.steps.rolllback.TerraformRollbackStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(CDP)
public class TerraformRollbackStepPlanCreator extends CDPMSStepPlanCreatorV2<TerraformRollbackStepNode> {
  @Inject private CDFeatureFlagHelper featureFlagService;

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.TERRAFORM_ROLLBACK);
  }

  @Override
  public Class<TerraformRollbackStepNode> getFieldClass() {
    return TerraformRollbackStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, TerraformRollbackStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, TerraformRollbackStepNode stepElement) {
    StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    if (featureFlagService.isEnabled(ctx.getMetadata().getAccountIdentifier(), FeatureName.EXPORT_TF_PLAN_JSON_NG)) {
      TerraformRollbackStepParameters tfRollbackStepParameters =
          (TerraformRollbackStepParameters) ((StepElementParameters) stepParameters).getSpec();
      List<YamlNode> planSteps = findStepsBeforeCurrentStep(
          ctx.getCurrentField(), stepNode -> StepSpecTypeConstants.TERRAFORM_PLAN.equals(stepNode.getType()));

      List<String> planStepsFqn = planSteps.stream().map(YamlUtils::getFullyQualifiedName).collect(Collectors.toList());

      tfRollbackStepParameters.setPlanStepsFqn(planStepsFqn);
    }

    return stepParameters;
  }
}
