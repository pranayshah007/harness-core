/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.aws.sam;

import static io.harness.cdng.visitor.YamlTypes.AWS_SAM_PUBLISH;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.sam.publish.AwsSamPublishStepNode;
import io.harness.cdng.aws.sam.publish.AwsSamPublishStepParameters;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class AwsSamPublishStepPlanCreator extends CDPMSStepPlanCreatorV2<AwsSamPublishStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(AWS_SAM_PUBLISH);
  }

  @Override
  public Class<AwsSamPublishStepNode> getFieldClass() {
    return AwsSamPublishStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, AwsSamPublishStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, AwsSamPublishStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    String awsSamValidateBuildPackageFqn =
        getExecutionStepFqn(ctx.getCurrentField(), YamlTypes.AWS_SAM_VALIDATE_BUILD_PACKAGE);

    AwsSamPublishStepParameters awsSamPublishStepParameters =
        (AwsSamPublishStepParameters) ((StepElementParameters) stepParameters).getSpec();
    awsSamPublishStepParameters.setAwsSamValidateBuildPackageFqn(awsSamValidateBuildPackageFqn);
    awsSamPublishStepParameters.setPublishCommandOptions(
        stepElement.getAwsSamPublishStepInfo().getPublishCommandOptions());
    awsSamPublishStepParameters.setDelegateSelectors(stepElement.getAwsSamPublishStepInfo().getDelegateSelectors());
    return stepParameters;
  }
}
