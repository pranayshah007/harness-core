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
import io.harness.beans.steps.stepinfo.DockerStepInfo;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactMetadata;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.steps.StepType;

@OwnedBy(HarnessTeam.CI)
public class DockerStep extends AbstractStepExecutable {
  public static final StepType STEP_TYPE = DockerStepInfo.STEP_TYPE;
  private static final String DOCKER_URL_FORMAT = "https://hub.docker.com/layers/%s/%s/images/%s/";
  @Override
  protected StepArtifacts handleArtifact(ArtifactMetadata artifactMetadata, StepElementParameters stepParameters) {
    StepArtifactsBuilder stepArtifactsBuilder = StepArtifacts.builder();
    if (artifactMetadata == null) {
      return stepArtifactsBuilder.build();
    }

    String identifier = stepParameters.getIdentifier();
    DockerStepInfo dockerStepInfo = (DockerStepInfo) stepParameters.getSpec();
    final String repo =
        resolveStringParameter("repo", "BuildAndPushDockerRegistry", identifier, dockerStepInfo.getRepo(), true);

    if (artifactMetadata.getType() == ArtifactMetadataType.DOCKER_ARTIFACT_METADATA) {
      DockerArtifactMetadata dockerArtifactMetadata = (DockerArtifactMetadata) artifactMetadata.getSpec();
      if (dockerArtifactMetadata != null && isNotEmpty(dockerArtifactMetadata.getDockerArtifacts())) {
        dockerArtifactMetadata.getDockerArtifacts().forEach(desc -> {
          String[] imageWithTag = desc.getImageName().split(":");
          String digest = desc.getDigest().replace(":", "-");
          String image = imageWithTag[0];
          String tag = imageWithTag[1];
          String url = null;
          if (isNotEmpty(dockerArtifactMetadata.getRegistryUrl())
              && dockerArtifactMetadata.getRegistryUrl().contains("index.docker.io")) {
            url = format(DOCKER_URL_FORMAT, repo, tag, digest);
          }
          stepArtifactsBuilder.publishedImageArtifact(
              PublishedImageArtifact.builder().imageName(image).tag(tag).url(url).digest(desc.getDigest()).build());
        });
      }
    }
    return stepArtifactsBuilder.build();
  }
}
