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
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.spec.server.ssca.v1.model.OrchestrationSummaryResponse;
import io.harness.ssca.cd.beans.orchestration.CdSscaOrchestrationStepOutcome;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.ssca.client.beans.SBOMArtifactResponse;
import io.harness.ssca.execution.orchestration.outcome.PublishedSbomArtifact;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.AbstractContainerStep;

import com.google.inject.Inject;
import java.util.Optional;

@OwnedBy(HarnessTeam.SSCA)
public class CdSscaOrchestrationStep extends AbstractContainerStep {
  @Inject private SSCAServiceUtils sscaServiceUtils;
  @Inject private SdkGraphVisualizationDataService sdkGraphVisualizationDataService;

  @Override
  public StepResponse.StepOutcome produceOutcome(Ambiance ambiance, StepElementParameters stepParameters) {
    Optional<Level> stageLevel = AmbianceUtils.getStageLevelFromAmbiance(ambiance);
    CdSscaOrchestrationStepOutcome stepOutcome = CdSscaOrchestrationStepOutcome.builder().build();
    if (stageLevel.isEmpty()) {
      throw new ContainerStepExecutionException("Could not fetch stage details");
    }

    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);

    if (sscaServiceUtils.isSSCAManagerEnabled()) {
      try {
        OrchestrationSummaryResponse response =
            sscaServiceUtils.getOrchestrationSummaryResponse(stepExecutionId, AmbianceUtils.getAccountId(ambiance),
                AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));

        if (response == null) {
          return null;
        }
        stepOutcome.setSbomArtifact(PublishedSbomArtifact.builder()
                                        .id(response.getArtifact().getId())
                                        .url(response.getArtifact().getRegistryUrl())
                                        .imageName(response.getArtifact().getName())
                                        .tag(response.getArtifact().getTag())
                                        .isSbomAttested(response.isIsAttested())
                                        .sbomName(response.getSbom().getName())
                                        .stepExecutionId(stepExecutionId)
                                        .build());
      } catch (CIStageExecutionException exception) {
        throw new ContainerStepExecutionException("Request to SSCA manager call failed", exception);
      }
    } else {
      try {
        SBOMArtifactResponse response =
            sscaServiceUtils.getSbomArtifact(stepExecutionId, AmbianceUtils.getAccountId(ambiance),
                AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
        if (response == null) {
          return null;
        }
        stepOutcome.setSbomArtifact(PublishedSbomArtifact.builder()
                                        .id(response.getArtifact().getId())
                                        .url(response.getArtifact().getUrl())
                                        .imageName(response.getArtifact().getName())
                                        .tag(response.getArtifact().getTag())
                                        .isSbomAttested(response.getAttestation().isAttested())
                                        .sbomName(response.getSbom().getName())
                                        .sbomUrl(response.getSbom().getUrl())
                                        .stepExecutionId(stepExecutionId)
                                        .build());
      } catch (CIStageExecutionException exception) {
        throw new ContainerStepExecutionException("Request to SSCA service call failed", exception);
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
