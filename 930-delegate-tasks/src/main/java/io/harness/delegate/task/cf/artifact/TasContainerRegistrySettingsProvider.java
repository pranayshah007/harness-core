/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cf.artifact;

import static io.harness.azure.model.AzureConstants.ACR_DEFAULT_DOCKER_USERNAME;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.azure.client.AzureAuthorizationClient;
import io.harness.azure.client.AzureContainerRegistryClient;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.utility.AzureUtils;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.task.azure.common.AzureConnectorMapper;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.NestedExceptionUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class TasContainerRegistrySettingsProvider extends AbstractTasRegistrySettingsProvider {
  @Inject private AzureConnectorMapper connectorMapper;
  @Inject private AzureContainerRegistryClient azureContainerRegistryClient;
  @Inject private AzureAuthorizationClient azureAuthorizationClient;
  @Inject DecryptionHelper decryptionHelper;

  @Override
  public TasArtifactCreds getContainerSettings(
      TasContainerArtifactConfig artifactConfig, DecryptionHelper decryptionHelper) {
    AzureConnectorDTO azureConnectorDTO = (AzureConnectorDTO) artifactConfig.getConnectorConfig();
    if (azureConnectorDTO.getCredential() == null) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Check if artifact is properly configured and not missing any details",
          format("No credentials configured for artifact of type '%s'", artifactConfig.getRegistryType().getValue()),
          new InvalidArgumentsException(Pair.of("credentials", "Missing credentials in configured artifact")));
    }
    decryptEntity(
        decryptionHelper, azureConnectorDTO.getDecryptableEntities(), artifactConfig.getEncryptedDataDetails());

    final AzureConfig azureConfig = connectorMapper.toAzureConfig(azureConnectorDTO);
    final String registryUrl = artifactConfig.getRegistryHostname();
    String username;
    String password;

    if (AzureAuthenticationType.SERVICE_PRINCIPAL_SECRET == azureConfig.getAzureAuthenticationType()) {
      username = azureConfig.getClientId();
      password = new String(azureConfig.getKey());
    } else {
      String accessToken =
          azureAuthorizationClient
              .getUserAccessToken(azureConfig,
                  AzureUtils.convertToScope(
                      AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType()).getManagementEndpoint()))
              .getAccessToken();

      username = ACR_DEFAULT_DOCKER_USERNAME;
      password = azureContainerRegistryClient.getAcrRefreshToken(artifactConfig.getRegistryHostname(), accessToken);
    }

    validateSettings(artifactConfig, registryUrl, username, password);
    return populateDockerSettings(registryUrl, username, password);
  }
}
