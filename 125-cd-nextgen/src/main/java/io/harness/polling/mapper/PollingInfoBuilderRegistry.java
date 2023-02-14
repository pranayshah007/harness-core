/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.polling.contracts.Type;
import io.harness.polling.mapper.artifact.AMIArtifactInfoBuilder;
import io.harness.polling.mapper.artifact.AcrArtifactInfoBuilder;
import io.harness.polling.mapper.artifact.ArtifactoryRegistryArtifactInfoBuilder;
import io.harness.polling.mapper.artifact.AzureArtifactsInfoBuilder;
import io.harness.polling.mapper.artifact.BambooArtifactInfoBuilder;
import io.harness.polling.mapper.artifact.CustomArtifactInfoBuilder;
import io.harness.polling.mapper.artifact.DockerHubArtifactInfoBuilder;
import io.harness.polling.mapper.artifact.EcrArtifactInfoBuilder;
import io.harness.polling.mapper.artifact.GarArtifactInfoBuilder;
import io.harness.polling.mapper.artifact.GcrArtifactInfoBuilder;
import io.harness.polling.mapper.artifact.GithubPackagesArtifactInfoBuilder;
import io.harness.polling.mapper.artifact.GoogleCloudStorageInfoBuilder;
import io.harness.polling.mapper.artifact.JenkinsArtifactInfoBuilder;
import io.harness.polling.mapper.artifact.Nexus2RegistryArtifactInfoBuilder;
import io.harness.polling.mapper.artifact.NexusRegistryArtifactInfoBuilder;
import io.harness.polling.mapper.artifact.S3ArtifactInfoBuilder;
import io.harness.polling.mapper.gitpolling.GitPollingInfoBuilder;
import io.harness.polling.mapper.manifest.GcsHelmChartManifestInfoBuilder;
import io.harness.polling.mapper.manifest.HttpHelmChartManifestInfoBuilder;
import io.harness.polling.mapper.manifest.S3HelmChartManifestInfoBuilder;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public class PollingInfoBuilderRegistry {
  @Inject private Injector injector;

  private final Map<Type, Class<? extends PollingInfoBuilder>> registeredPollingInfoBuilders =
      new EnumMap<>(Type.class);

  public PollingInfoBuilderRegistry() {
    registeredPollingInfoBuilders.put(Type.HTTP_HELM, HttpHelmChartManifestInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.S3_HELM, S3HelmChartManifestInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.GCS_HELM, GcsHelmChartManifestInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.ECR, EcrArtifactInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.DOCKER_HUB, DockerHubArtifactInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.GCR, GcrArtifactInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.NEXUS3, NexusRegistryArtifactInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.ARTIFACTORY, ArtifactoryRegistryArtifactInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.ACR, AcrArtifactInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.AMAZON_S3, S3ArtifactInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.JENKINS, JenkinsArtifactInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.GIT_POLL, GitPollingInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.CUSTOM_ARTIFACT, CustomArtifactInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.GOOGLE_ARTIFACT_REGISTRY, GarArtifactInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.GITHUB_PACKAGES, GithubPackagesArtifactInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.NEXUS2, Nexus2RegistryArtifactInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.AZURE_ARTIFACTS, AzureArtifactsInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.AMI, AMIArtifactInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.GOOGLE_CLOUD_STORAGE_ARTIFACT, GoogleCloudStorageInfoBuilder.class);
    registeredPollingInfoBuilders.put(Type.BAMBOO, BambooArtifactInfoBuilder.class);
  }

  public Optional<PollingInfoBuilder> getPollingInfoBuilder(Type type) {
    if (!registeredPollingInfoBuilders.containsKey(type)) {
      return Optional.empty();
    }
    return Optional.of(injector.getInstance(registeredPollingInfoBuilders.get(type)));
  }
}
