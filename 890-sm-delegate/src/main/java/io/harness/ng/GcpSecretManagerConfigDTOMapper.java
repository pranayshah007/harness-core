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
import io.harness.delegate.beans.connector.gcpsecretmanager.GcpSecretManagerConnectorDTO;
import io.harness.secretmanagerclient.dto.GcpSecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.GcpSecretManagerConfigUpdateDTO;
import io.harness.security.encryption.EncryptionType;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class GcpSecretManagerConfigDTOMapper {
  public static GcpSecretManagerConfigDTO getGcpSecretManagerConfigDTO(String accountIdentifier,
      ConnectorDTO connectorRequestDTO, GcpSecretManagerConnectorDTO gcpSecretManagerConnectorDTO) {
    ConnectorInfoDTO connector = connectorRequestDTO.getConnectorInfo();

    GcpSecretManagerConfigDTO gcpSecretManagerConfigDTO =
        GcpSecretManagerConfigDTO.builder()
            .delegateSelectors(gcpSecretManagerConnectorDTO.getDelegateSelectors())
            .isDefault(gcpSecretManagerConnectorDTO.isDefault())
            .encryptionType(EncryptionType.GCP_SECRETS_MANAGER)
            .assumeCredentialsOnDelegate(gcpSecretManagerConnectorDTO.getAssumeCredentialsOnDelegate())

            .name(connector.getName())
            .accountIdentifier(accountIdentifier)
            .orgIdentifier(connector.getOrgIdentifier())
            .projectIdentifier(connector.getProjectIdentifier())
            .tags(connector.getTags())
            .identifier(connector.getIdentifier())
            .description(connector.getDescription())
            .build();

    if (null != gcpSecretManagerConnectorDTO.getCredentialsRef().getDecryptedValue()) {
      gcpSecretManagerConfigDTO.setCredentials(gcpSecretManagerConnectorDTO.getCredentialsRef().getDecryptedValue());
    }
    return gcpSecretManagerConfigDTO;
  }

  public static GcpSecretManagerConfigUpdateDTO getGcpSecretManagerConfigUpdateDTO(
      ConnectorDTO connectorRequestDTO, GcpSecretManagerConnectorDTO gcpSecretManagerConnectorDTO) {
    ConnectorInfoDTO connector = connectorRequestDTO.getConnectorInfo();
    GcpSecretManagerConfigUpdateDTO gcpSecretManagerConfigUpdateDTO =
        GcpSecretManagerConfigUpdateDTO.builder()
            .delegateSelectors(gcpSecretManagerConnectorDTO.getDelegateSelectors())
            .isDefault(gcpSecretManagerConnectorDTO.isDefault())

            .name(connector.getName())
            .encryptionType(EncryptionType.GCP_SECRETS_MANAGER)
            .tags(connector.getTags())
            .description(connector.getDescription())
            .build();

    if (null != gcpSecretManagerConnectorDTO.getCredentialsRef().getDecryptedValue()) {
      gcpSecretManagerConfigUpdateDTO.setCredentials(
          gcpSecretManagerConnectorDTO.getCredentialsRef().getDecryptedValue());
    }
    return gcpSecretManagerConfigUpdateDTO;
  }
}