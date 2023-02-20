/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.aws.sam;

import static io.harness.cdng.visitor.YamlTypes.AWS_SAM_VALIDATE_BUILD_PACKAGE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.sam.validateBuildPackage.AwsSamValidateBuildPackageStepNode;
import io.harness.cdng.aws.sam.validateBuildPackage.AwsSamValidateBuildPackageStepParameters;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class AwsSamValidateBuildPackagePlanCreator extends CDPMSStepPlanCreatorV2<AwsSamValidateBuildPackageStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(AWS_SAM_VALIDATE_BUILD_PACKAGE);
  }

  @Override
  public Class<AwsSamValidateBuildPackageStepNode> getFieldClass() {
    return AwsSamValidateBuildPackageStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(
      PlanCreationContext ctx, AwsSamValidateBuildPackageStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, AwsSamValidateBuildPackageStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    AwsSamValidateBuildPackageStepParameters awsSamValidateBuildPackageStepParameters =
        (AwsSamValidateBuildPackageStepParameters) ((StepElementParameters) stepParameters).getSpec();
    awsSamValidateBuildPackageStepParameters.setDelegateSelectors(
        stepElement.getAwsSamValidateBuildPackageStepInfo().getDelegateSelectors());
    awsSamValidateBuildPackageStepParameters.setValidateCommandOptions(
        stepElement.getAwsSamValidateBuildPackageStepInfo().getValidateCommandOptions());
    awsSamValidateBuildPackageStepParameters.setBuildCommandOptions(
        stepElement.getAwsSamValidateBuildPackageStepInfo().getBuildCommandOptions());
    awsSamValidateBuildPackageStepParameters.setPackageCommandOptions(
        stepElement.getAwsSamValidateBuildPackageStepInfo().getPackageCommandOptions());
    return stepParameters;
  }
}
