/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static io.harness.azure.model.AzureConstants.ACR_DEFAULT_DOCKER_USERNAME;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.client.AzureAuthorizationClient;
import io.harness.azure.client.AzureContainerRegistryClient;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.utility.AzureUtils;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.task.azure.common.AzureConnectorMapper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.NestedExceptionUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AzureContainerRegistrySettingsProvider extends AbstractAzureRegistrySettingsProvider {
  @Inject private AzureConnectorMapper connectorMapper;
  @Inject private AzureContainerRegistryClient azureContainerRegistryClient;
  @Inject private AzureAuthorizationClient azureAuthorizationClient;

  @Override
  public Map<String, AzureAppServiceApplicationSetting> getContainerSettings(
      AzureContainerArtifactConfig artifactConfig) {
    AzureConnectorDTO azureConnectorDTO = (AzureConnectorDTO) artifactConfig.getConnectorConfig();
    if (azureConnectorDTO.getCredential() == null) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Check if artifact is properly configured and not missing any details",
          format("No credentials configured for artifact of type '%s'", artifactConfig.getRegistryType().getValue()),
          new InvalidArgumentsException(Pair.of("credentials", "Missing credentials in configured artifact")));
    }

    final AzureConfig azureConfig = connectorMapper.toAzureConfig(azureConnectorDTO);
    final String registryUrl = artifactConfig.getRegistryHostname();
    String username;
    String password;

    if (AzureAuthenticationType.SERVICE_PRINCIPAL_SECRET == azureConfig.getAzureAuthenticationType()) {
      username = azureConfig.getClientId();
      password = new String(azureConfig.getKey());
    } else {
      String accessToken;
      if (AzureAuthenticationType.SERVICE_PRINCIPAL_CERT == azureConfig.getAzureAuthenticationType()) {
        accessToken = azureAuthorizationClient.getUserAccessToken(azureConfig, AzureUtils.AUTH_SCOPE).getAccessToken();
      } else {
        // only MSI connection will/should reach here
        accessToken = azureAuthorizationClient.getUserAccessToken(azureConfig, null).getAccessToken();
      }

      username = ACR_DEFAULT_DOCKER_USERNAME;
      password = azureContainerRegistryClient.getAcrRefreshToken(artifactConfig.getRegistryHostname(), accessToken);
    }

    validateSettings(artifactConfig, registryUrl, username, password);
    return populateDockerSettingMap(registryUrl, username, password);
  }
}
