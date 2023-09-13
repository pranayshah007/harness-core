/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.ssca;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.beans.steps.outcome.StepArtifacts.StepArtifactsBuilder;
import static io.harness.ssca.beans.SscaConstants.PREDICATE_TYPE;
import static io.harness.ssca.beans.SscaConstants.SLSA_VERIFICATION;
import static io.harness.ssca.beans.SscaConstants.SLSA_VERIFICATION_STEP_TYPE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ProvenanceArtifact;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.provenance.ProvenancePredicate;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.ci.execution.states.AbstractStepExecutable;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.ProvenanceMetaData;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.serializer.JsonUtils;
import io.harness.slsa.beans.verification.source.SlsaDockerSourceSpec;
import io.harness.slsa.beans.verification.source.SlsaVerificationSourceType;
import io.harness.ssca.beans.stepinfo.SlsaVerificationStepInfo;

import java.util.Map;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.SSCA)
public class SlsaVerificationStep extends AbstractStepExecutable {
  public static final StepType STEP_TYPE = SLSA_VERIFICATION_STEP_TYPE;

  protected boolean shouldPublishArtifact(StepStatus stepStatus) {
    return true;
  }

  protected boolean shouldPublishOutcome(StepStatus stepStatus) {
    return true;
  }

  @Override
  protected void modifyStepStatus(Ambiance ambiance, StepStatus stepStatus, String stepIdentifier) {
    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String outputKey = "SLSA_PROVENANCE_" + stepExecutionId;
    StepMapOutput stepOutput = (StepMapOutput) stepStatus.getOutput();

    if (stepOutput != null && stepOutput.getMap() != null && stepOutput.getMap().containsKey(outputKey)) {
      stepStatus.setArtifactMetadata(
          ArtifactMetadata.builder()
              .type(ArtifactMetadataType.PROVENANCE_ARTIFACT_METADATA)
              .spec(ProvenanceMetaData.builder().provenance(stepOutput.getMap().get(outputKey)).build())
              .build());

      stepOutput.setMap(stepOutput.getMap()
                            .entrySet()
                            .stream()
                            .filter(entry -> !entry.getKey().equals(outputKey))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }
  }

  @Override
  protected StepArtifacts handleArtifact(
      ArtifactMetadata artifactMetadata, StepElementParameters stepParameters, Ambiance ambiance) {
    StepArtifactsBuilder stepArtifactsBuilder = StepArtifacts.builder();
    SlsaVerificationStepInfo slsaVerificationStepInfo = (SlsaVerificationStepInfo) stepParameters.getSpec();
    if (slsaVerificationStepInfo != null && slsaVerificationStepInfo.getSource() != null
        && SlsaVerificationSourceType.DOCKER.equals(slsaVerificationStepInfo.getSource().getType())) {
      String identifier = stepParameters.getIdentifier();
      SlsaDockerSourceSpec slsaDockerSourceSpec = (SlsaDockerSourceSpec) slsaVerificationStepInfo.getSource().getSpec();
      String imageName = resolveStringParameter(
          "image_path", SLSA_VERIFICATION, identifier, slsaDockerSourceSpec.getImage_path(), true);
      String tag = resolveStringParameter("tag", SLSA_VERIFICATION, identifier, slsaDockerSourceSpec.getTag(), true);

      stepArtifactsBuilder.publishedImageArtifact(
          PublishedImageArtifact.builder().imageName(imageName).tag(tag).build());
    }

    if (artifactMetadata != null
        && ArtifactMetadataType.PROVENANCE_ARTIFACT_METADATA.equals(artifactMetadata.getType())) {
      ProvenanceMetaData provenanceMetaData = (ProvenanceMetaData) artifactMetadata.getSpec();
      ProvenancePredicate predicate = JsonUtils.asObject(provenanceMetaData.getProvenance(), ProvenancePredicate.class);
      stepArtifactsBuilder.provenanceArtifact(
          ProvenanceArtifact.builder().predicateType(PREDICATE_TYPE).predicate(predicate).build());
    }
    return stepArtifactsBuilder.build();
  }
}
