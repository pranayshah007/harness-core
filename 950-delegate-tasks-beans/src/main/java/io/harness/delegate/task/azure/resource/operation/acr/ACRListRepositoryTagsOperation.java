/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.azure.resource.operation.acr;

import static io.harness.azure.model.AzureConstants.REGISTRY_NAME_BLANK_VALIDATION_MSG;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.azure.client.AzureContainerRegistryClient;
import io.harness.azure.client.AzureResourceClient;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.task.azure.resource.operation.AzureOperationName;
import io.harness.delegate.task.azure.resource.operation.AzureResourceOperation;
import io.harness.delegate.task.azure.resource.operation.AzureResourceOperationResponse;
import io.harness.delegate.task.azure.resource.operation.AzureResourceProvider;
import io.harness.exception.InvalidRequestException;

import com.azure.resourcemanager.containerregistry.models.Registry;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@EqualsAndHashCode
@Slf4j
public class ACRListRepositoryTagsOperation implements AzureResourceOperation {
  @NotNull private String subscriptionId;
  @NotNull private String registryName;
  @NotNull private String repositoryName;

  @Override
  public AzureResourceProvider getAzureResourceProvider() {
    return AzureResourceProvider.CONTAINER_REGISTRY;
  }

  @Override
  public AzureOperationName getOperationName() {
    return AzureOperationName.LIST_ACR_REPOSITORY_TAGS;
  }

  @Override
  public <T extends AzureResourceClient> AzureResourceOperationResponse executeOperation(
      T resourceClient, AzureConfig azureConfig) {
    if (isBlank(registryName)) {
      throw new IllegalArgumentException(REGISTRY_NAME_BLANK_VALIDATION_MSG);
    }

    log.info("Start executing operation on delegate, operationName: {}, operationDetails: {}",
        getOperationName().getValue(), this);
    List<Registry> containerRegistries =
        ((AzureContainerRegistryClient) resourceClient).listContainerRegistries(azureConfig, subscriptionId);

    Registry registry =
        containerRegistries.stream()
            .filter(Objects::nonNull)
            .filter(containerRegistryName -> registryName.equals(containerRegistryName.name()))
            .findFirst()
            .orElseThrow(()
                             -> new InvalidRequestException(
                                 format("Unable to find container registry, registryName: %s on subscriptionId: %s",
                                     registryName, subscriptionId)));

    List<String> repositoryTags = ((AzureContainerRegistryClient) resourceClient)
                                      .listRepositoryTags(azureConfig, registry.loginServerUrl(), repositoryName);
    return ACRListRepositoryTagsOperationResponse.builder().repositoryTags(repositoryTags).build();
  }
}
