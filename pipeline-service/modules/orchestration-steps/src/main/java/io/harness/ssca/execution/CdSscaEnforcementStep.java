/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.spec.server.ssca.v1.model.EnforcementSummaryResponse;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.cd.beans.enforcement.CdSscaEnforcementStepOutcome;
import io.harness.ssca.client.SSCAServiceClient;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.ssca.client.beans.enforcement.SscaEnforcementSummary;
import io.harness.ssca.execution.orchestration.outcome.PublishedSbomArtifact;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.AbstractContainerStep;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.SSCA)
public class CdSscaEnforcementStep extends AbstractContainerStep {
  public static final StepType STEP_TYPE = SscaConstants.CD_SSCA_ENFORCEMENT_STEP_TYPE;
  @Inject private SSCAServiceClient sscaServiceClient;
  @Inject private SSCAServiceUtils sscaServiceUtils;

  @Inject private SdkGraphVisualizationDataService sdkGraphVisualizationDataService;

  @Override
  public StepResponse.StepOutcome produceOutcome(Ambiance ambiance, StepElementParameters stepParameters) {
    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    CdSscaEnforcementStepOutcome stepOutcome = CdSscaEnforcementStepOutcome.builder().build();

    if (sscaServiceUtils.isSSCAManagerEnabled()) {
      try {
        EnforcementSummaryResponse sscaEnforcementSummary =
            sscaServiceUtils.getEnforcementSummary(stepExecutionId, AmbianceUtils.getAccountId(ambiance),
                AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
        if (sscaEnforcementSummary == null) {
          throw new ContainerStepExecutionException(
              "Could not fetch enforcement summary from SSCA manager. Response body is null");
        }
        stepOutcome.setSbomArtifact(PublishedSbomArtifact.builder()
                                        .id(sscaEnforcementSummary.getArtifact().getId())
                                        .url(sscaEnforcementSummary.getArtifact().getRegistryUrl())
                                        .imageName(sscaEnforcementSummary.getArtifact().getName())
                                        .tag(sscaEnforcementSummary.getArtifact().getTag())
                                        .denyListViolationCount(sscaEnforcementSummary.getDenyListViolationCount())
                                        .allowListViolationCount(sscaEnforcementSummary.getAllowListViolationCount())
                                        .stepExecutionId(stepExecutionId)
                                        .build());
      } catch (CIStageExecutionException exception) {
        throw new ContainerStepExecutionException(String.format(
            "Could not fetch enforcement summary from SSCA manager. Error message: %s", exception.getMessage()));
      }
    } else {
      SscaEnforcementSummary sscaEnforcementSummary = sscaServiceUtils.getEnforcementSummary(stepExecutionId);
      if (sscaEnforcementSummary == null) {
        throw new ContainerStepExecutionException(
            "Could not fetch enforcement summary from SSCA service. Response body is null");
      }
      stepOutcome.setSbomArtifact(PublishedSbomArtifact.builder()
                                      .id(sscaEnforcementSummary.getArtifact().getId())
                                      .url(sscaEnforcementSummary.getArtifact().getUrl())
                                      .imageName(sscaEnforcementSummary.getArtifact().getName())
                                      .tag(sscaEnforcementSummary.getArtifact().getTag())
                                      .denyListViolationCount(sscaEnforcementSummary.getDenyListViolationCount())
                                      .allowListViolationCount(sscaEnforcementSummary.getAllowListViolationCount())
                                      .stepExecutionId(stepExecutionId)
                                      .build());
      try {
      } catch (CIStageExecutionException exception) {
        throw new ContainerStepExecutionException(String.format(
            "Could not fetch enforcement summary from SSCA service. Error message: %s", exception.getMessage()));
      }
    }
    String outputName = "artifact_" + stepExecutionId;
    sdkGraphVisualizationDataService.publishStepDetailInformation(
        ambiance, stepOutcome, outputName, StepCategory.STAGE);
    return StepResponse.StepOutcome.builder()
        .outcome(stepOutcome)
        .name(outputName)
        .group(StepOutcomeGroup.STAGE.name())
        .build();
  }
}
