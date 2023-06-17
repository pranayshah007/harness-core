/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps.serverless;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.serverless.container.steps.DownloadServerlessManifestsV2StepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class DownloadServerlessManifestsV2StepPlanCreator
    extends CDPMSStepPlanCreatorV2<DownloadServerlessManifestsV2StepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.DOWNLOAD_SERVERLESS_MANIFESTS);
  }

  @Override
  public Class<DownloadServerlessManifestsV2StepNode> getFieldClass() {
    return DownloadServerlessManifestsV2StepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(
      PlanCreationContext ctx, DownloadServerlessManifestsV2StepNode stepNode) {
    return super.createPlanForField(ctx, stepNode);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, DownloadServerlessManifestsV2StepNode stepNode) {
    return super.getStepParameters(ctx, stepNode);
  }
}
