/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.polling.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.ami.AMIArtifactTaskHandler;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactTaskHandler;
import io.harness.delegate.task.artifacts.azure.AcrArtifactTaskHandler;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsTaskHandler;
import io.harness.delegate.task.artifacts.bamboo.BambooArtifactTaskHandler;
import io.harness.delegate.task.artifacts.custom.CustomArtifactTaskHandler;
import io.harness.delegate.task.artifacts.docker.DockerArtifactTaskHandler;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactTaskHandler;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactTaskHandler;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactTaskHandler;
import io.harness.delegate.task.artifacts.googleartifactregistry.GARArtifactTaskHandler;
import io.harness.delegate.task.artifacts.googlecloudstorage.GoogleCloudStorageArtifactTaskHandler;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactTaskHandler;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactTaskHandler;
import io.harness.delegate.task.artifacts.s3.S3ArtifactTaskHandler;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public class ArtifactCollectionServiceRegistryNg {
  @Inject Injector injector;

  public DelegateArtifactTaskHandler getBuildService(ArtifactSourceType artifactSourceType) {
    Class<? extends DelegateArtifactTaskHandler> buildServiceClass = getBuildServiceClass(artifactSourceType);
    return injector.getInstance(Key.get(buildServiceClass));
  }

  public Class<? extends DelegateArtifactTaskHandler> getBuildServiceClass(ArtifactSourceType artifactSourceType) {
    switch (artifactSourceType) {
      case DOCKER_REGISTRY:
        return DockerArtifactTaskHandler.class;
      case ECR:
        return EcrArtifactTaskHandler.class;
      case GCR:
        return GcrArtifactTaskHandler.class;
      case NEXUS3_REGISTRY:
      case NEXUS2_REGISTRY:
        return NexusArtifactTaskHandler.class;
      case ARTIFACTORY_REGISTRY:
        return ArtifactoryArtifactTaskHandler.class;
      case ACR:
        return AcrArtifactTaskHandler.class;
      case AMAZONS3:
        return S3ArtifactTaskHandler.class;
      case JENKINS:
        return JenkinsArtifactTaskHandler.class;
      case CUSTOM_ARTIFACT:
        return CustomArtifactTaskHandler.class;
      case GITHUB_PACKAGES:
        return GithubPackagesArtifactTaskHandler.class;
      case GOOGLE_ARTIFACT_REGISTRY:
        return GARArtifactTaskHandler.class;
      case AZURE_ARTIFACTS:
        return AzureArtifactsTaskHandler.class;
      case AMI:
        return AMIArtifactTaskHandler.class;
      case GOOGLE_CLOUD_STORAGE_ARTIFACT:
        return GoogleCloudStorageArtifactTaskHandler.class;
      case BAMBOO:
        return BambooArtifactTaskHandler.class;
      default:
        throw new InvalidRequestException("Unknown artifact source type: " + artifactSourceType);
    }
  }
}
