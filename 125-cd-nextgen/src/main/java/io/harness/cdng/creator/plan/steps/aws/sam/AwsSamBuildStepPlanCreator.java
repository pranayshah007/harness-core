/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.aws.sam;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.sam.AwsSamBuildStepNode;
import io.harness.cdng.aws.sam.AwsSamBuildStepParameters;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.ecs.EcsRollingRollbackStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

import static io.harness.cdng.visitor.YamlTypes.DOWNLOAD_MANIFESTS;
import static io.harness.cdng.visitor.YamlTypes.ECS_ROLLING_DEPLOY;

@OwnedBy(HarnessTeam.CDP)
public class AwsSamBuildStepPlanCreator extends CDPMSStepPlanCreatorV2<AwsSamBuildStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.AWS_SAM_BUILD);
  }

  @Override
  public Class<AwsSamBuildStepNode> getFieldClass() {
    return AwsSamBuildStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, AwsSamBuildStepNode stepNode) {
    return super.createPlanForField(ctx, stepNode);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, AwsSamBuildStepNode stepNode) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepNode);
    String downloadManifestsFqn = getExecutionStepFqn(ctx.getCurrentField(), DOWNLOAD_MANIFESTS);
    AwsSamBuildStepParameters awsSamBuildStepParameters =
            (AwsSamBuildStepParameters) ((StepElementParameters) stepParameters).getSpec();
    awsSamBuildStepParameters.setDownloadManifestsFqn(downloadManifestsFqn);
    awsSamBuildStepParameters.setDelegateSelectors(
            stepNode.getAwsSamBuildStepInfo().getDelegateSelectors());
    return stepParameters;
  }
}
