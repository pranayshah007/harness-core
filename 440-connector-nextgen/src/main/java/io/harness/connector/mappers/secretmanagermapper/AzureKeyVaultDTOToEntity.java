/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.secretmanagermapper;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.azurekeyvaultconnector.AzureKeyVaultConnector;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO;
import io.harness.encryption.SecretRefHelper;

@OwnedBy(PL)
public class AzureKeyVaultDTOToEntity
    implements ConnectorDTOToEntityMapper<AzureKeyVaultConnectorDTO, AzureKeyVaultConnector> {
  @Override
  public AzureKeyVaultConnector toConnectorEntity(AzureKeyVaultConnectorDTO connectorDTO) {
    return AzureKeyVaultConnector.builder()
        .isDefault(connectorDTO.isDefault())
        .clientId(connectorDTO.getClientId())
        .tenantId(connectorDTO.getTenantId())
        .vaultName(connectorDTO.getVaultName())
        .secretKeyRef(SecretRefHelper.getSecretConfigString(connectorDTO.getSecretKey()))
        .vaultConfiguredManually(connectorDTO.isVaultConfiguredManually())
        .subscription(connectorDTO.getSubscription())
        .azureEnvironmentType(connectorDTO.getAzureEnvironmentType())
        .useManagedIdentity(connectorDTO.getUseManagedIdentity())
        .managedClientId(connectorDTO.getManagedClientId())
        .azureManagedIdentityType(connectorDTO.getAzureManagedIdentityType())
        .build();
  }
}
