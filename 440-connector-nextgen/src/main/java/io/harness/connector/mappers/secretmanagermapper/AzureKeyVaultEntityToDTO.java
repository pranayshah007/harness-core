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
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO;
import io.harness.encryption.SecretRefHelper;

@OwnedBy(PL)
public class AzureKeyVaultEntityToDTO
    implements ConnectorEntityToDTOMapper<AzureKeyVaultConnectorDTO, AzureKeyVaultConnector> {
  @Override
  public AzureKeyVaultConnectorDTO createConnectorDTO(AzureKeyVaultConnector connector) {
    return AzureKeyVaultConnectorDTO.builder()
        .isDefault(connector.isDefault())
        .clientId(connector.getClientId())
        .tenantId(connector.getTenantId())
        .vaultName(connector.getVaultName())
        .secretKey(SecretRefHelper.createSecretRef(connector.getSecretKeyRef()))
        .vaultConfiguredManually(connector.isVaultConfiguredManually())
        .subscription(connector.getSubscription())
        .azureEnvironmentType(connector.getAzureEnvironmentType())
        .delegateSelectors(connector.getDelegateSelectors())
        .useManagedIdentity(connector.getUseManagedIdentity())
        .managedClientId(connector.getManagedClientId())
        .azureManagedIdentityType(connector.getAzureManagedIdentityType())
        .build();
  }
}
