/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.azureblobconnector.AzureBlobConnectorDTO;
import io.harness.secretmanagerclient.dto.azureblob.AzureBlobConfigDTO;
import io.harness.secretmanagerclient.dto.azureblob.AzureBlobConfigUpdateDTO;
import io.harness.security.encryption.EncryptionType;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class AzureBlobConfigDTOMapper {
  public static AzureBlobConfigUpdateDTO getAzureBlobConfigUpdateDTO(
      ConnectorDTO connectorRequestDTO, AzureBlobConnectorDTO azureBlobConnectorDTO) {
    azureBlobConnectorDTO.validate();
    ConnectorInfoDTO connector = connectorRequestDTO.getConnectorInfo();
    AzureBlobConfigUpdateDTO azureBlobConfigUpdateDTO =
        AzureBlobConfigUpdateDTO.builder()
            .clientId(azureBlobConnectorDTO.getClientId())
            .tenantId(azureBlobConnectorDTO.getTenantId())
            .azureEnvironmentType(azureBlobConnectorDTO.getAzureEnvironmentType())
            .isDefault(false)
            .name(connector.getName())
            .encryptionType(EncryptionType.AZURE_VAULT)
            .tags(connector.getTags())
            .description(connector.getDescription())
            .build();
    if (null != azureBlobConnectorDTO.getSecretKey().getDecryptedValue()) {
      azureBlobConfigUpdateDTO.setSecretKey(String.valueOf(azureBlobConnectorDTO.getSecretKey().getDecryptedValue()));
    }
    return azureBlobConfigUpdateDTO;
  }

  public static AzureBlobConfigDTO getAzureBlobConfigDTO(
      String accountIdentifier, ConnectorDTO connectorRequestDTO, AzureBlobConnectorDTO azureBlobConnectorDTO) {
    azureBlobConnectorDTO.validate();
    ConnectorInfoDTO connector = connectorRequestDTO.getConnectorInfo();
    AzureBlobConfigDTO azureBlobConfigDTO = AzureBlobConfigDTO.builder()
                                                .clientId(azureBlobConnectorDTO.getClientId())
                                                .tenantId(azureBlobConnectorDTO.getTenantId())
                                                .containerURL(azureBlobConnectorDTO.getContainerURL())
                                                .azureEnvironmentType(azureBlobConnectorDTO.getAzureEnvironmentType())
                                                .isDefault(false)
                                                .encryptionType(EncryptionType.AZURE_BLOB)
                                                .delegateSelectors(azureBlobConnectorDTO.getDelegateSelectors())

                                                .name(connector.getName())
                                                .accountIdentifier(accountIdentifier)
                                                .orgIdentifier(connector.getOrgIdentifier())
                                                .projectIdentifier(connector.getProjectIdentifier())
                                                .tags(connector.getTags())
                                                .identifier(connector.getIdentifier())
                                                .description(connector.getDescription())
                                                .build();
    if (null != azureBlobConnectorDTO.getSecretKey().getDecryptedValue()) {
      azureBlobConfigDTO.setSecretKey(String.valueOf(azureBlobConnectorDTO.getSecretKey().getDecryptedValue()));
    }
    return azureBlobConfigDTO;
  }
}
