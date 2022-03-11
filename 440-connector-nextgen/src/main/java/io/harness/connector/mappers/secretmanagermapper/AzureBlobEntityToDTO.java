/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.secretmanagermapper;

import io.harness.connector.entities.embedded.azureblobconnector.AzureBlobConnector;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.azureblobconnector.AzureBlobConnectorDTO;
import io.harness.encryption.SecretRefHelper;

public class AzureBlobEntityToDTO implements ConnectorEntityToDTOMapper<AzureBlobConnectorDTO, AzureBlobConnector> {
  @Override
  public AzureBlobConnectorDTO createConnectorDTO(AzureBlobConnector connector) {
    return AzureBlobConnectorDTO.builder()
        .isDefault(connector.isDefault())
        .clientId(connector.getClientId())
        .tenantId(connector.getTenantId())
        .vaultName(connector.getVaultName())
        .secretKey(SecretRefHelper.createSecretRef(connector.getSecretKeyRef()))
        .subscription(connector.getSubscription())
        .connectionString(connector.getConnectionString())
        .containerName(connector.getContainerName())
        .keyId(connector.getKeyId())
        .azureEnvironmentType(connector.getAzureEnvironmentType())
        .delegateSelectors(connector.getDelegateSelectors())
        .build();
  }
}
