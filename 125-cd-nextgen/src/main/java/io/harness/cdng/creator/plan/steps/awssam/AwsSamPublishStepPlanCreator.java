/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.awssam;

import static io.harness.cdng.visitor.YamlTypes.AWS_SAM_PUBLISH;
import static io.harness.cdng.visitor.YamlTypes.ECS_ROLLING_DEPLOY;

import io.harness.cdng.awssam.publish.AwsSamPublishStepNode;
import io.harness.cdng.awssam.publish.AwsSamPublishStepParameters;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

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

    String awsSamBuildAndPackageFnq = getExecutionStepFqn(ctx.getCurrentField(), YamlTypes.AWS_SAM_BUILD_PACKAGE);

    AwsSamPublishStepParameters awsSamPublishStepParameters =
        (AwsSamPublishStepParameters) ((StepElementParameters) stepParameters).getSpec();
    awsSamPublishStepParameters.setAwsSamBuildAndPackageFnq(awsSamBuildAndPackageFnq);
    awsSamPublishStepParameters.setPublishCommandOptions(
        stepElement.getAwsSamPublishStepInfo().getPublishCommandOptions());
    awsSamPublishStepParameters.setDelegateSelectors(stepElement.getAwsSamPublishStepInfo().getDelegateSelectors());
    return stepParameters;
  }
}
