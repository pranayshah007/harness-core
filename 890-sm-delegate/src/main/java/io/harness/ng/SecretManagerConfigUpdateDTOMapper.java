/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.AwsKmsConfigDTOMapper.getAwsKmsConfigUpdateDTO;
import static io.harness.ng.AzureKeyVaultConfigDTOMapper.getAzureKeyVaultConfigUpdateDTO;
import static io.harness.ng.GcpKmsConfigDTOMapper.getGcpKmsConfigUpdateDTO;
import static io.harness.ng.VaultConfigDTOMapper.getVaultConfigUpdateDTO;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsConnectorDTO;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO;
import io.harness.delegate.beans.connector.gcpkmsconnector.GcpKmsConnectorDTO;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.secretmanagerclient.dto.SecretManagerConfigUpdateDTO;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class SecretManagerConfigUpdateDTOMapper {
  public static SecretManagerConfigUpdateDTO fromConnectorDTO(
      ConnectorDTO connectorRequestDTO, ConnectorConfigDTO connectorConfigDTO) {
    ConnectorInfoDTO connector = connectorRequestDTO.getConnectorInfo();
    if (connector.getConnectorType() == ConnectorType.VAULT) {
      return getVaultConfigUpdateDTO(connectorRequestDTO, (VaultConnectorDTO) connectorConfigDTO);
    } else if (connector.getConnectorType() == ConnectorType.GCP_KMS) {
      return getGcpKmsConfigUpdateDTO(connectorRequestDTO, (GcpKmsConnectorDTO) connectorConfigDTO);
    } else if (connector.getConnectorType() == ConnectorType.AWS_KMS) {
      return getAwsKmsConfigUpdateDTO(connectorRequestDTO, (AwsKmsConnectorDTO) connectorConfigDTO);
    } else if (connector.getConnectorType() == ConnectorType.AZURE_KEY_VAULT) {
      return getAzureKeyVaultConfigUpdateDTO(connectorRequestDTO, (AzureKeyVaultConnectorDTO) connectorConfigDTO);
    } else if (connector.getConnectorType() == ConnectorType.LOCAL) {
      throw new InvalidRequestException("Update operation not supported for Local Secret Manager");
    }
    throw new IllegalArgumentException("This is not a valid secret manager type: " + connector.getConnectorType());
  }
}
