/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.outcome.StepArtifacts.StepArtifactsBuilder;
import io.harness.beans.steps.stepinfo.GARStepInfo;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactMetadata;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.steps.StepType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@OwnedBy(HarnessTeam.CI)
public class GARStep extends AbstractStepExecutable {
  public static final StepType STEP_TYPE = GARStepInfo.STEP_TYPE;
  private static final String GAR_URL_FORMAT =
      "https://console.cloud.google.com/artifacts/docker/%s/%s/%s@%s?project=%s";
  private static final String GAR_HOST_REGEX = "^(?:(?<region>[a-zA-Z]+)\\.)?docker\\.pkg.dev$";
  private static final String GAR_GLOBAL_REGION = "GLOBAL";
  public static final String REGION = "region";

  @Override
  protected StepArtifacts handleArtifact(ArtifactMetadata artifactMetadata, StepElementParameters stepParameters) {
    StepArtifactsBuilder stepArtifactsBuilder = StepArtifacts.builder();
    if (artifactMetadata == null) {
      return stepArtifactsBuilder.build();
    }

    String identifier = stepParameters.getIdentifier();
    GARStepInfo garStepInfo = (GARStepInfo) stepParameters.getSpec();
    final String projectID =
        resolveStringParameter("projectID", "BuildAndPushGAR", identifier, garStepInfo.getProjectID(), true);
    final String host = resolveStringParameter(REGION, "BuildAndPushGAR", identifier, garStepInfo.getHost(), true);

    final Pattern pattern = Pattern.compile(GAR_HOST_REGEX, Pattern.CASE_INSENSITIVE);
    final Matcher matcher = pattern.matcher(host);
    final String region =
        (matcher.find() && matcher.group(REGION) != null) ? matcher.group(REGION).toUpperCase() : GAR_GLOBAL_REGION;

    if (artifactMetadata.getType() == ArtifactMetadataType.DOCKER_ARTIFACT_METADATA) {
      processDockerArtifactMetadata(artifactMetadata, stepArtifactsBuilder, projectID, region);
    }
    return stepArtifactsBuilder.build();
  }

  private static void processDockerArtifactMetadata(
      ArtifactMetadata artifactMetadata, StepArtifactsBuilder stepArtifactsBuilder, String projectID, String region) {
    DockerArtifactMetadata dockerArtifactMetadata = (DockerArtifactMetadata) artifactMetadata.getSpec();
    if (dockerArtifactMetadata != null && isNotEmpty(dockerArtifactMetadata.getDockerArtifacts())) {
      dockerArtifactMetadata.getDockerArtifacts().forEach(desc -> {
        int colonIndex = desc.getImageName().lastIndexOf(":");
        String image = desc.getImageName().substring(0, colonIndex);
        String tag = desc.getImageName().substring(colonIndex + 1);
        image = image.replaceAll("^" + Pattern.quote(dockerArtifactMetadata.getRegistryUrl()) + "/", "");

        stepArtifactsBuilder.publishedImageArtifact(
            PublishedImageArtifact.builder()
                .imageName(image)
                .tag(tag)
                .url(format(GAR_URL_FORMAT, projectID, region, image, desc.getDigest(), projectID))
                .digest(desc.getDigest())
                .build());
      });
    }
  }
}
