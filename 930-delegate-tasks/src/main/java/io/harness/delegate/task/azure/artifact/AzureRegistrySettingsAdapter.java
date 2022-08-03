/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.NestedExceptionUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AzureRegistrySettingsAdapter {
  @Inject private AzureDockerHubPublicRegistrySettingsProvider dockerHubPublicRegistrySettingsProvider;
  @Inject private AzureDockerHubPrivateRegistrySettingsProvider dockerHubPrivateRegistrySettingsProvider;
  @Inject private AzureArtifactoryRegistrySettingsProvider artifactoryRegistrySettingsProvider;
  @Inject private AzureContainerRegistrySettingsProvider azureContainerRegistrySettingsProvider;
  @Inject private AzureElasticContainerRegistrySettingsProvider azureElasticContainerRegistrySettingsProvider;
  @Inject private AzureGoogleContainerRegistrySettingsProvider azureGoogleContainerRegistrySettingsProvider;
  @Inject private AzureNexus3RegistrySettingsProvider azureNexus3RegistrySettingsProvider;

  public Map<String, AzureAppServiceApplicationSetting> getContainerSettings(
      AzureContainerArtifactConfig artifactConfig) {
    switch (artifactConfig.getRegistryType()) {
      case DOCKER_HUB_PUBLIC:
        return dockerHubPublicRegistrySettingsProvider.getContainerSettings(artifactConfig);
      case DOCKER_HUB_PRIVATE:
        return dockerHubPrivateRegistrySettingsProvider.getContainerSettings(artifactConfig);
      case ARTIFACTORY_PRIVATE_REGISTRY:
        return artifactoryRegistrySettingsProvider.getContainerSettings(artifactConfig);
      case ACR:
        return azureContainerRegistrySettingsProvider.getContainerSettings(artifactConfig);
      case ECR:
        return azureElasticContainerRegistrySettingsProvider.getContainerSettings(artifactConfig);
      case GCR:
        return azureGoogleContainerRegistrySettingsProvider.getContainerSettings(artifactConfig);
      case NEXUS_PRIVATE_REGISTRY:
        return azureNexus3RegistrySettingsProvider.getContainerSettings(artifactConfig);
      default:
        throw NestedExceptionUtils.hintWithExplanationException(
            "Use a different container registry supported by Harness",
            format("Container registry of type '%s' is not supported", artifactConfig.getRegistryType().getValue()),
            new InvalidArgumentsException(Pair.of("registryType", "Unsupported registry type")));
    }
  }
}
