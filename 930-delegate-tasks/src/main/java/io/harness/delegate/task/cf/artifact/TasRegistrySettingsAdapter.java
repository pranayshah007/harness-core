/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cf.artifact;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.NestedExceptionUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class TasRegistrySettingsAdapter {
  @Inject private TasDockerHubPublicRegistrySettingsProvider dockerHubPublicRegistrySettingsProvider;
  @Inject private TasDockerHubPrivateRegistrySettingsProvider dockerHubPrivateRegistrySettingsProvider;
  @Inject private TasArtifactoryRegistrySettingsProvider artifactoryRegistrySettingsProvider;
  @Inject private TasContainerRegistrySettingsProvider tasContainerRegistrySettingsProvider;
  @Inject private TasElasticContainerRegistrySettingsProvider tasElasticContainerRegistrySettingsProvider;
  @Inject private TasGoogleContainerRegistrySettingsProvider tasGoogleContainerRegistrySettingsProvider;
  @Inject private TasGoogleArtifactRegistrySettingsProvider tasGoogleArtifactRegistrySettingsProvider;
  @Inject private TasNexus3RegistrySettingsProvider tasNexus3RegistrySettingsProvider;
  @Inject private TasGithubPackageRegistrySettingsProvider tasGithubPackageRegistrySettingsProvider;
  @Inject DecryptionHelper decryptionHelper;

  public TasArtifactCreds getContainerSettings(TasContainerArtifactConfig artifactConfig) {
    if (isNull(artifactConfig.getRegistryType())) {
      throw NestedExceptionUtils.hintWithExplanationException("Please contact Harness support team",
          format("Unexpected null artifact configuration for TAS containers"),
          new InvalidArgumentsException(Pair.of("artifactConfig", "Null artifact config")));
    }
    switch (artifactConfig.getRegistryType()) {
      case DOCKER_HUB_PUBLIC:
        return dockerHubPublicRegistrySettingsProvider.getContainerSettings(artifactConfig, decryptionHelper);
      case DOCKER_HUB_PRIVATE:
        return dockerHubPrivateRegistrySettingsProvider.getContainerSettings(artifactConfig, decryptionHelper);
      case ARTIFACTORY_PRIVATE_REGISTRY:
        return artifactoryRegistrySettingsProvider.getContainerSettings(artifactConfig, decryptionHelper);
      case ACR:
        return tasContainerRegistrySettingsProvider.getContainerSettings(artifactConfig, decryptionHelper);
      case ECR:
        return tasElasticContainerRegistrySettingsProvider.getContainerSettings(artifactConfig, decryptionHelper);
      case GCR:
        return tasGoogleContainerRegistrySettingsProvider.getContainerSettings(artifactConfig, decryptionHelper);
      case GAR:
        return tasGoogleArtifactRegistrySettingsProvider.getContainerSettings(artifactConfig, decryptionHelper);
      case NEXUS_PRIVATE_REGISTRY:
        return tasNexus3RegistrySettingsProvider.getContainerSettings(artifactConfig, decryptionHelper);
      case GITHUB_PACKAGE_REGISTRY:
        return tasGithubPackageRegistrySettingsProvider.getContainerSettings(artifactConfig, decryptionHelper);
      default:
        throw NestedExceptionUtils.hintWithExplanationException(
            "Use a different container registry supported by Harness",
            format("Container registry of type '%s' is not supported", artifactConfig.getRegistryType().getValue()),
            new InvalidArgumentsException(Pair.of("registryType", "Unsupported registry type")));
    }
  }
}
