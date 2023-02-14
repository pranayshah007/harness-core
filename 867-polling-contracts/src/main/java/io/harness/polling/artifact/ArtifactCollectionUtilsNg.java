/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifacts.S3ArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.ami.AMIArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryGenericArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsDelegateResponse;
import io.harness.delegate.task.artifacts.bamboo.BambooArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.custom.CustomArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.gar.GarDelegateResponse;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.googlecloudstorage.GoogleCloudStorageArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.nexus.Nexus2ArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public class ArtifactCollectionUtilsNg {
  // TODO: Move to a common package which ng manager and delegate can depend on
  public static String getArtifactKey(ArtifactDelegateResponse artifactDelegateResponse) {
    switch (artifactDelegateResponse.getSourceType()) {
      case DOCKER_REGISTRY:
        return ((DockerArtifactDelegateResponse) artifactDelegateResponse).getTag();
      case ECR:
        return ((EcrArtifactDelegateResponse) artifactDelegateResponse).getTag();
      case GCR:
        return ((GcrArtifactDelegateResponse) artifactDelegateResponse).getTag();
      case NEXUS3_REGISTRY:
        return ((NexusArtifactDelegateResponse) artifactDelegateResponse).getTag();
      case ARTIFACTORY_REGISTRY:
        if (artifactDelegateResponse instanceof ArtifactoryGenericArtifactDelegateResponse) {
          return ((ArtifactoryGenericArtifactDelegateResponse) artifactDelegateResponse).getArtifactPath();
        } else {
          return ((ArtifactoryArtifactDelegateResponse) artifactDelegateResponse).getTag();
        }
      case ACR:
        return ((AcrArtifactDelegateResponse) artifactDelegateResponse).getTag();
      case AMAZONS3:
        return ((S3ArtifactDelegateResponse) artifactDelegateResponse).getFilePath();
      case JENKINS:
        return ((JenkinsArtifactDelegateResponse) artifactDelegateResponse).getBuild();
      case CUSTOM_ARTIFACT:
        return ((CustomArtifactDelegateResponse) artifactDelegateResponse).getVersion();
      case GOOGLE_ARTIFACT_REGISTRY:
        return ((GarDelegateResponse) artifactDelegateResponse).getVersion();
      case GITHUB_PACKAGES:
        return ((GithubPackagesArtifactDelegateResponse) artifactDelegateResponse).getVersion();
      case NEXUS2_REGISTRY:
        return ((Nexus2ArtifactDelegateResponse) artifactDelegateResponse).getTag();
      case AZURE_ARTIFACTS:
        return ((AzureArtifactsDelegateResponse) artifactDelegateResponse).getVersion();
      case AMI:
        return ((AMIArtifactDelegateResponse) artifactDelegateResponse).getVersion();
      case GOOGLE_CLOUD_STORAGE_ARTIFACT:
        return ((GoogleCloudStorageArtifactDelegateResponse) artifactDelegateResponse).getArtifactPath();
      case BAMBOO:
        return ((BambooArtifactDelegateResponse) artifactDelegateResponse).getBuild();
      default:
        throw new InvalidRequestException(
            String.format("Source type %s not supported", artifactDelegateResponse.getSourceType()));
    }
  }
}
