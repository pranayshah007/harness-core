/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.outcome.StepArtifacts.StepArtifactsBuilder;
import io.harness.beans.steps.stepinfo.ACRStepInfo;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactMetadata;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.steps.StepType;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.StringUtils;

public class ACRStep extends AbstractStepExecutable {
  public static final StepType STEP_TYPE = ACRStepInfo.STEP_TYPE;

  @Override
  protected StepArtifacts handleArtifact(ArtifactMetadata artifactMetadata, StepElementParameters stepParameters) {
    StepArtifactsBuilder stepArtifactsBuilder = StepArtifacts.builder();
    if (artifactMetadata == null) {
      return stepArtifactsBuilder.build();
    }

    String identifier = stepParameters.getIdentifier();
    ACRStepInfo acrStepInfo = (ACRStepInfo) stepParameters.getSpec();
    String repository =
        resolveStringParameter("repository", "BuildAndPushACR", identifier, acrStepInfo.getRepository(), true);
    String subscriptionId =
        resolveStringParameter("subscriptionId", "BuildAndPushACR", identifier, acrStepInfo.getSubscriptionId(), false);

    // for backward compatibility check if the subscription id is not there, don't publish acr artifact because we will
    // not have the correct url.
    if (StringUtils.isEmpty(subscriptionId)) {
      return stepArtifactsBuilder.build();
    }
    String registryUrl = repository.substring(0, repository.indexOf('/'));
    String folder = repository.substring(repository.indexOf('/') + 1);

    if (artifactMetadata.getType() == ArtifactMetadataType.DOCKER_ARTIFACT_METADATA) {
      DockerArtifactMetadata dockerArtifactMetadata = (DockerArtifactMetadata) artifactMetadata.getSpec();
      if (dockerArtifactMetadata != null && isNotEmpty(dockerArtifactMetadata.getDockerArtifacts())) {
        dockerArtifactMetadata.getDockerArtifacts().forEach(desc -> {
          String digest = desc.getDigest();
          String[] imageWithTag = desc.getImageName().split(":");
          String image = imageWithTag[0];
          String tag = imageWithTag[1];

          try {
            stepArtifactsBuilder.publishedImageArtifact(
                PublishedImageArtifact.builder()
                    .imageName(image)
                    .tag(tag)
                    .url(dockerArtifactMetadata.getRegistryUrl() + "/repositoryName/"
                        + URLEncoder.encode(folder, StandardCharsets.UTF_8.name()) + "/tag/" + tag + "/loginServer/"
                        + registryUrl)
                    .build());
          } catch (UnsupportedEncodingException e) {
            // ignore the error
          }
        });
      }
    }
    return stepArtifactsBuilder.build();
  }
}
