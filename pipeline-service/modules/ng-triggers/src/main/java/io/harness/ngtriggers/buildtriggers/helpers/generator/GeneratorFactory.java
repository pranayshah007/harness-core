/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.source.ManifestType.HELM_MANIFEST;
import static io.harness.ngtriggers.beans.source.NGTriggerType.ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MANIFEST;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MULTI_REGION_ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.WEBHOOK;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.ACR;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.AMAZON_S3;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.AMI;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.ARTIFACTORY_REGISTRY;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.AZURE_ARTIFACTS;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.BAMBOO;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.CUSTOM_ARTIFACT;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.DOCKER_REGISTRY;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.ECR;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.GCR;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.GITHUB_PACKAGES;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.GOOGLE_CLOUD_STORAGE;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.GoogleArtifactRegistry;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.JENKINS;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.NEXUS2_REGISTRY;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.NEXUS3_REGISTRY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.artifact.BuildStoreType;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
public class GeneratorFactory {
  private final BuildTriggerHelper buildTriggerHelper;
  private final HttpHelmPollingItemGenerator httpHelmPollingItemGenerator;
  private final S3HelmPollingItemGenerator s3HelmPollingItemGenerator;
  private final S3PollingItemGenerator s3PollingItemGenerator;
  private final GCSHelmPollingItemGenerator gcsHelmPollingItemGenerator;
  private final GcrPollingItemGenerator gcrPollingItemGenerator;
  private final EcrPollingItemGenerator ecrPollingItemGenerator;
  private final DockerRegistryPollingItemGenerator dockerRegistryPollingItemGenerator;
  private final ArtifactoryRegistryPollingItemGenerator artifactoryRegistryPollingItemGenerator;
  private final AcrPollingItemGenerator acrPollingItemGenerator;
  private final JenkinsPollingItemGenerator jenkinsPollingItemGenerator;
  private final GitPollingItemGenerator gitPollingItemGenerator;
  private final CustomPollingItemGenerator customPollingItemGenerator;
  private final GARPollingItemGenerator garPollingItemGenerator;
  private final GithubPackagesPollingItemGenerator githubPackagesPollingItemGenerator;
  private final Nexus2RegistryPollingItemGenerator nexus2RegistryPollingItemGenerator;
  private final Nexus3PollingItemGenerator nexus3PollingItemGenerator;
  private final AzureArtifactsPollingItemGenerator azureArtifactsPollingItemGenerator;
  private final AMIPollingItemGenerator amiPollingItemGenerator;
  private final GoogleCloudStoragePollingItemGenerator googleCloudStoragePollingItemGenerator;
  private final BambooPollingItemGenerator bambooPollingItemGenerator;

  public PollingItemGenerator retrievePollingItemGenerator(BuildTriggerOpsData buildTriggerOpsData) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();
    NGTriggerType triggerType = ngTriggerEntity.getType();
    if (triggerType == MANIFEST) {
      return retrievePollingItemGeneratorForManifest(buildTriggerOpsData);
    } else if (triggerType == ARTIFACT || triggerType == MULTI_REGION_ARTIFACT) {
      return retrievePollingItemGeneratorForArtifact(buildTriggerOpsData);
    } else if (triggerType == WEBHOOK) {
      return retrievePollingItemGeneratorForGitPolling(buildTriggerOpsData);
    }

    return null;
  }

  private PollingItemGenerator retrievePollingItemGeneratorForArtifact(BuildTriggerOpsData buildTriggerOpsData) {
    String buildType = buildTriggerHelper.fetchBuildType(buildTriggerOpsData.getTriggerSpecMap());
    if (GCR.getValue().equals(buildType)) {
      return gcrPollingItemGenerator;
    } else if (ECR.getValue().equals(buildType)) {
      return ecrPollingItemGenerator;
    } else if (DOCKER_REGISTRY.getValue().equals(buildType)) {
      return dockerRegistryPollingItemGenerator;
    } else if (ARTIFACTORY_REGISTRY.getValue().equals(buildType)) {
      return artifactoryRegistryPollingItemGenerator;
    } else if (ACR.getValue().equals(buildType)) {
      return acrPollingItemGenerator;
    } else if (AMAZON_S3.getValue().equals(buildType)) {
      return s3PollingItemGenerator;
    } else if (JENKINS.getValue().equals(buildType)) {
      return jenkinsPollingItemGenerator;
    } else if (CUSTOM_ARTIFACT.getValue().equals(buildType)) {
      return customPollingItemGenerator;
    } else if (GoogleArtifactRegistry.getValue().equals(buildType)) {
      return garPollingItemGenerator;
    } else if (GITHUB_PACKAGES.getValue().equals(buildType)) {
      return githubPackagesPollingItemGenerator;
    } else if (NEXUS2_REGISTRY.getValue().equals(buildType)) {
      return nexus2RegistryPollingItemGenerator;
    } else if (NEXUS3_REGISTRY.getValue().equals(buildType)) {
      return nexus3PollingItemGenerator;
    } else if (AZURE_ARTIFACTS.getValue().equals(buildType)) {
      return azureArtifactsPollingItemGenerator;
    } else if (AMI.getValue().equals(buildType)) {
      return amiPollingItemGenerator;
    } else if (GOOGLE_CLOUD_STORAGE.getValue().equals(buildType)) {
      return googleCloudStoragePollingItemGenerator;
    } else if (BAMBOO.getValue().equals(buildType)) {
      return bambooPollingItemGenerator;
    }
    return null;
  }

  private PollingItemGenerator retrievePollingItemGeneratorForManifest(BuildTriggerOpsData buildTriggerOpsData) {
    String buildType = buildTriggerHelper.fetchBuildType(buildTriggerOpsData.getTriggerSpecMap());
    if (HELM_MANIFEST.getValue().equals(buildType)) {
      return retrievePollingItemGeneratorForHelmChart(buildTriggerOpsData);
    }

    return null;
  }

  private PollingItemGenerator retrievePollingItemGeneratorForHelmChart(BuildTriggerOpsData buildTriggerOpsData) {
    String storeTypeFromTrigger = buildTriggerHelper.fetchStoreTypeForHelm(buildTriggerOpsData);
    if (BuildStoreType.HTTP.getValue().equals(storeTypeFromTrigger)) {
      return httpHelmPollingItemGenerator;
    } else if (BuildStoreType.S3.getValue().equals(storeTypeFromTrigger)) {
      return s3HelmPollingItemGenerator;
    } else if (BuildStoreType.GCS.getValue().equals(storeTypeFromTrigger)) {
      return gcsHelmPollingItemGenerator;
    }

    return null;
  }

  private PollingItemGenerator retrievePollingItemGeneratorForGitPolling(BuildTriggerOpsData buildTriggerOpsData) {
    return gitPollingItemGenerator;
  }
}
