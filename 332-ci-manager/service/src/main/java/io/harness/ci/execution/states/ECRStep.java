/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.outcome.StepArtifacts.StepArtifactsBuilder;
import io.harness.beans.steps.stepinfo.ECRStepInfo;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactMetadata;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.steps.StepType;

import java.util.regex.Pattern;

@OwnedBy(HarnessTeam.CI)
public class ECRStep extends AbstractStepExecutable {
  public static final StepType STEP_TYPE = ECRStepInfo.STEP_TYPE;
  private static final String ECR_URL_FORMAT =
      "https://console.aws.amazon.com/ecr/repositories/private/%s/%s/image/%s/details/?region=%s";

  @Override
  protected StepArtifacts handleArtifact(ArtifactMetadata artifactMetadata, StepElementParameters stepParameters) {
    StepArtifactsBuilder stepArtifactsBuilder = StepArtifacts.builder();
    if (artifactMetadata == null) {
      return stepArtifactsBuilder.build();
    }

    String identifier = stepParameters.getIdentifier();
    ECRStepInfo ecrStepInfo = (ECRStepInfo) stepParameters.getSpec();
    final String account =
        resolveStringParameter("account", "BuildAndPushECR", identifier, ecrStepInfo.getAccount(), true);
    final String region =
        resolveStringParameter("region", "BuildAndPushECR", identifier, ecrStepInfo.getRegion(), true);

    if (artifactMetadata.getType() == ArtifactMetadataType.DOCKER_ARTIFACT_METADATA) {
      DockerArtifactMetadata dockerArtifactMetadata = (DockerArtifactMetadata) artifactMetadata.getSpec();
      if (dockerArtifactMetadata != null && isNotEmpty(dockerArtifactMetadata.getDockerArtifacts())) {
        dockerArtifactMetadata.getDockerArtifacts().forEach(desc -> {
          String[] imageWithTag = desc.getImageName().split(":");
          String digest = desc.getDigest();
          String image = imageWithTag[0];
          String tag = imageWithTag[1];
          image = image.replaceFirst("^" + Pattern.quote(dockerArtifactMetadata.getRegistryUrl()) + "/?", "");

          stepArtifactsBuilder.publishedImageArtifact(PublishedImageArtifact.builder()
                                                          .imageName(image)
                                                          .tag(tag)
                                                          .url(format(ECR_URL_FORMAT, account, image, digest, region))
                                                          .digest(digest)
                                                          .build());
        });
      }
    }
    return stepArtifactsBuilder.build();
  }
}
