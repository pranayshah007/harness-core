/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.ssca.cd.beans.orchestration.CdSscaOrchestrationStepOutcome;
import io.harness.ssca.client.SSCAServiceClient;
import io.harness.ssca.client.beans.SBOMArtifactResponse;
import io.harness.ssca.execution.orchestration.outcome.PublishedSbomArtifact;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.AbstractContainerStep;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.SSCA)
public class CdSscaOrchestrationStep extends AbstractContainerStep {
  @Inject private SSCAServiceClient sscaServiceClient;
  @Inject private SdkGraphVisualizationDataService sdkGraphVisualizationDataService;

  @Override
  public StepResponse.StepOutcome produceOutcome(Ambiance ambiance, StepElementParameters stepParameters) {
    Optional<Level> stageLevel = AmbianceUtils.getStageLevelFromAmbiance(ambiance);

    if (stageLevel.isEmpty()) {
      throw new ContainerStepExecutionException("Could not fetch stage details");
    }

    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    SBOMArtifactResponse response = getSbomArtifactResponse(ambiance, stepExecutionId);

    if (response == null) {
      return null;
    }

    CdSscaOrchestrationStepOutcome stepOutcome =
        CdSscaOrchestrationStepOutcome.builder()
            .sbomArtifact(PublishedSbomArtifact.builder()
                              .id(response.getArtifact().getId())
                              .url(response.getArtifact().getUrl())
                              .imageName(response.getArtifact().getName())
                              .isSbomAttested(response.getAttestation().isAttested())
                              .sbomName(response.getSbom().getName())
                              .sbomUrl(response.getSbom().getUrl())
                              .stepExecutionId(stepExecutionId)
                              .build())
            .build();

    String outputName = "artifact_" + stepExecutionId;
    sdkGraphVisualizationDataService.publishStepDetailInformation(
        ambiance, stepOutcome, outputName, StepCategory.STAGE);
    return StepResponse.StepOutcome.builder()
        .outcome(stepOutcome)
        .name(outputName)
        .group(StepOutcomeGroup.STAGE.name())
        .build();
  }

  private SBOMArtifactResponse getSbomArtifactResponse(Ambiance ambiance, String stepExecutionId) {
    Call<SBOMArtifactResponse> call =
        sscaServiceClient.getArtifactInfoV2(stepExecutionId, AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));

    try {
      Response<SBOMArtifactResponse> response = call.execute();
      return response.body();
    } catch (IOException exception) {
      throw new ContainerStepExecutionException("Request to SSCA service call failed", exception);
    }
  }
}
