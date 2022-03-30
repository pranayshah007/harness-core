/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.secretmanagermapper;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.azureblobconnector.AzureBlobConnector;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.azureblobconnector.AzureBlobConnectorDTO;
import io.harness.encryption.SecretRefHelper;

@OwnedBy(PL)
public class AzureBlobDTOToEntity implements ConnectorDTOToEntityMapper<AzureBlobConnectorDTO, AzureBlobConnector> {
  @Override
  public AzureBlobConnector toConnectorEntity(AzureBlobConnectorDTO connectorDTO) {
    return AzureBlobConnector.builder()
        .isDefault(connectorDTO.isDefault())
        .clientId(connectorDTO.getClientId())
        .tenantId(connectorDTO.getTenantId())
        .vaultName(connectorDTO.getVaultName())
        .secretKeyRef(SecretRefHelper.getSecretConfigString(connectorDTO.getSecretKey()))
        .subscription(connectorDTO.getSubscription())
        .connectionString(connectorDTO.getConnectionString())
        .containerName(connectorDTO.getContainerName())
        .keyId(connectorDTO.getKeyId())
        .keyName(connectorDTO.getKeyName())
        .azureEnvironmentType(connectorDTO.getAzureEnvironmentType())
        .build();
  }
}
