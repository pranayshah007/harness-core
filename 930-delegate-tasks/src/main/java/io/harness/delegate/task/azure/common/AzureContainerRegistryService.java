/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.common;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.ACR_ACCESS_KEYS_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.ACR_USERNAME_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME;
import static io.harness.azure.model.AzureConstants.DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.client.AzureContainerRegistryClient;
import io.harness.azure.context.AzureContainerRegistryClientContext;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.beans.connector.azureconnector.AzureContainerRegistryConnectorDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.azure.management.containerregistry.AccessKeyType;
import com.microsoft.azure.management.containerregistry.Registry;
import com.microsoft.azure.management.containerregistry.RegistryCredentials;
import java.util.Map;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Singleton
@NoArgsConstructor
@Slf4j
@OwnedBy(CDP)
public class AzureContainerRegistryService {
  @Inject private AzureContainerRegistryClient azureContainerRegistryClient;

  public void updateACRDockerSettingByCredentials(AzureContainerRegistryConnectorDTO connectorConfigDTO,
      AzureConfig azureConfig, Map<String, AzureAppServiceApplicationSetting> dockerSettings) {
    RegistryCredentials registryCredentials = getContainerRegistryCredentials(azureConfig, connectorConfigDTO);
    updateACRDockerSettingByCredentials(dockerSettings, registryCredentials);
  }

  public RegistryCredentials getContainerRegistryCredentials(
      AzureConfig azureConfig, AzureContainerRegistryConnectorDTO connectorConfigDTO) {
    String azureRegistryName = connectorConfigDTO.getAzureRegistryName();
    String subscriptionId = connectorConfigDTO.getSubscriptionId();
    String resourceGroupName = fixResourceGroupName(azureConfig, connectorConfigDTO, azureRegistryName, subscriptionId);
    log.info(
        "Start getting container registry credentials azureRegistryName: {}, resourceGroupName: {}, subscriptionId: {}",
        azureRegistryName, resourceGroupName, subscriptionId);
    Optional<RegistryCredentials> containerRegistryCredentialsOp =
        azureContainerRegistryClient.getContainerRegistryCredentials(AzureContainerRegistryClientContext.builder()
                                                                         .azureConfig(azureConfig)
                                                                         .subscriptionId(subscriptionId)
                                                                         .registryName(azureRegistryName)
                                                                         .resourceGroupName(resourceGroupName)
                                                                         .build());

    return containerRegistryCredentialsOp.orElseThrow(
        ()
            -> new InvalidRequestException(format(
                "Not found container registry credentials, azureRegistryName: %s, subscriptionId: %s, resourceGroupName: %s ",
                azureRegistryName, subscriptionId, resourceGroupName)));
  }

  private void updateACRDockerSettingByCredentials(
      Map<String, AzureAppServiceApplicationSetting> dockerSettings, RegistryCredentials registryCredentials) {
    String username = getACRUsername(registryCredentials);
    String accessKey = getACRAccessKey(registryCredentials);

    dockerSettings.put(DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME,
        AzureAppServiceApplicationSetting.builder()
            .name(DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME)
            .sticky(false)
            .value(username)
            .build());

    dockerSettings.put(DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME,
        AzureAppServiceApplicationSetting.builder()
            .name(DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME)
            .sticky(false)
            .value(accessKey)
            .build());
  }

  @NotNull
  private String getACRUsername(RegistryCredentials registryCredentials) {
    String username = registryCredentials.username();
    if (isBlank(username)) {
      throw new InvalidArgumentsException(ACR_USERNAME_BLANK_VALIDATION_MSG);
    }
    return username;
  }

  @NotNull
  private String getACRAccessKey(RegistryCredentials registryCredentials) {
    Map<AccessKeyType, String> accessKeyTypeStringMap = registryCredentials.accessKeys();
    String accessKey = accessKeyTypeStringMap.get(AccessKeyType.PRIMARY);

    if (isBlank(accessKey)) {
      log.warn("ACR primary access key is null or empty trying to use secondary");
      accessKey = accessKeyTypeStringMap.get(AccessKeyType.SECONDARY);
    }

    if (isBlank(accessKey)) {
      throw new InvalidArgumentsException(ACR_ACCESS_KEYS_BLANK_VALIDATION_MSG);
    }
    return accessKey;
  }

  private String fixResourceGroupName(AzureConfig azureConfig, AzureContainerRegistryConnectorDTO acrConnectorConfigDTO,
      String azureRegistryName, String subscriptionId) {
    String resourceGroupName = acrConnectorConfigDTO.getResourceGroupName();
    if (isBlank(resourceGroupName)) {
      log.info(
          "Resource group name is blank, start filtering subscription by container registry name: {}, subscriptionId: {}",
          azureRegistryName, subscriptionId);
      Optional<Registry> registryOp = azureContainerRegistryClient.findFirstContainerRegistryByNameOnSubscription(
          azureConfig, subscriptionId, azureRegistryName);
      Registry registry =
          registryOp.orElseThrow(()
                                     -> new InvalidRequestException(
                                         format("Not found Azure container registry by name: %s, subscription id: %s",
                                             azureRegistryName, subscriptionId)));
      resourceGroupName = registry.resourceGroupName();
    }
    return resourceGroupName;
  }
}
