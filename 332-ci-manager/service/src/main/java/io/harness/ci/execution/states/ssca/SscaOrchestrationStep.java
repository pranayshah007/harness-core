/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.ssca;

import static io.harness.beans.steps.outcome.StepArtifacts.StepArtifactsBuilder;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.ci.states.AbstractStepExecutable;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.SscaArtifactMetadata;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.client.SSCAServiceClient;
import io.harness.ssca.client.beans.SBOMArtifactResponse;
import io.harness.ssca.execution.orchestration.outcome.PublishedSbomArtifact;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.SSCA)
public class SscaOrchestrationStep extends AbstractStepExecutable {
  public static final StepType STEP_TYPE = SscaConstants.SSCA_ORCHESTRATION_STEP_TYPE;
  private SSCAServiceClient sscaServiceClient;

  @Inject
  public SscaOrchestrationStep(SSCAServiceClient sscaServiceClient) {
    this.sscaServiceClient = sscaServiceClient;
  }

  @Override
  protected void modifyStepStatus(Ambiance ambiance, StepStatus stepStatus, String stepIdentifier) {
    Optional<Level> stageLevel = AmbianceUtils.getStageLevelFromAmbiance(ambiance);

    if (stageLevel.isEmpty()) {
      throw new CIStageExecutionException("Could not fetch stage details");
    }

    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    Call<SBOMArtifactResponse> call =
        sscaServiceClient.getArtifactInfoV2(stepExecutionId, AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));

    Response<SBOMArtifactResponse> response = null;
    try {
      response = call.execute();
    } catch (IOException e) {
      throw new CIStageExecutionException("Request to SSCA service call failed", e);
    }
    SBOMArtifactResponse sbomArtifactResponse = response.body();

    if (sbomArtifactResponse == null) {
      return;
    }

    stepStatus.setArtifactMetadata(ArtifactMetadata.builder()
                                       .type(ArtifactMetadataType.SSCA_ARTIFACT_METADATA)
                                       .spec(SscaArtifactMetadata.builder()
                                                 .id(sbomArtifactResponse.getArtifact().getId())
                                                 .imageName(sbomArtifactResponse.getArtifact().getName())
                                                 .registryUrl(sbomArtifactResponse.getArtifact().getUrl())
                                                 .registryType(sbomArtifactResponse.getArtifact().getType())
                                                 .isSbomAttested(sbomArtifactResponse.getAttestation().isAttested())
                                                 .sbomName(sbomArtifactResponse.getSbom().getName())
                                                 .sbomUrl(sbomArtifactResponse.getSbom().getUrl())
                                                 .stepExecutionId(stepExecutionId)
                                                 .build())
                                       .build());
  }

  @Override
  protected StepArtifacts handleArtifact(ArtifactMetadata artifactMetadata, StepElementParameters stepParameters) {
    StepArtifactsBuilder stepArtifactsBuilder = StepArtifacts.builder();
    if (artifactMetadata == null) {
      return stepArtifactsBuilder.build();
    }
    if (artifactMetadata.getType() == ArtifactMetadataType.SSCA_ARTIFACT_METADATA) {
      SscaArtifactMetadata sscaArtifactMetadata = (SscaArtifactMetadata) artifactMetadata.getSpec();
      if (sscaArtifactMetadata != null) {
        stepArtifactsBuilder.publishedSbomArtifact(PublishedSbomArtifact.builder()
                                                       .id(sscaArtifactMetadata.getId())
                                                       .url(sscaArtifactMetadata.getRegistryUrl())
                                                       .digest(sscaArtifactMetadata.getDigest())
                                                       .imageName(sscaArtifactMetadata.getImageName())
                                                       .isSbomAttested(sscaArtifactMetadata.isSbomAttested())
                                                       .sbomName(sscaArtifactMetadata.getSbomName())
                                                       .sbomUrl(sscaArtifactMetadata.getSbomUrl())
                                                       .stepExecutionId(sscaArtifactMetadata.getStepExecutionId())
                                                       .build());
      }
    }
    return stepArtifactsBuilder.build();
  }
}
