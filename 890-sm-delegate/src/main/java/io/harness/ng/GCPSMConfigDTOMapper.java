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
import io.harness.delegate.beans.connector.gcpsecretmanager.GcpSMConnectorDTO;
import io.harness.secretmanagerclient.dto.GCPSMConfigDTO;
import io.harness.secretmanagerclient.dto.GCPSMConfigUpdateDTO;
import io.harness.security.encryption.EncryptionType;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class GCPSMConfigDTOMapper {
  public static GCPSMConfigDTO getGCPSMConfigDTO(
      String accountIdentifier, ConnectorDTO connectorRequestDTO, GcpSMConnectorDTO gcpSMConnectorDTO) {
    ConnectorInfoDTO connector = connectorRequestDTO.getConnectorInfo();

    GCPSMConfigDTO gcpsmConfigDTO = GCPSMConfigDTO.builder()
                                        .delegateSelectors(gcpSMConnectorDTO.getDelegateSelectors())
                                        .isDefault(gcpSMConnectorDTO.isDefault())
                                        .encryptionType(EncryptionType.GCP_SECRETS_MANAGER)

                                        .name(connector.getName())
                                        .accountIdentifier(accountIdentifier)
                                        .orgIdentifier(connector.getOrgIdentifier())
                                        .projectIdentifier(connector.getProjectIdentifier())
                                        .tags(connector.getTags())
                                        .identifier(connector.getIdentifier())
                                        .description(connector.getDescription())
                                        .build();

    if (null != gcpSMConnectorDTO.getCredentials().getDecryptedValue()) {
      gcpsmConfigDTO.setCredentials(gcpSMConnectorDTO.getCredentials().getDecryptedValue());
    }
    return gcpsmConfigDTO;
  }

  public static GCPSMConfigUpdateDTO getGCPSMConfigUpdateDTO(
      ConnectorDTO connectorRequestDTO, GcpSMConnectorDTO gcpSMConnectorDTO) {
    ConnectorInfoDTO connector = connectorRequestDTO.getConnectorInfo();
    GCPSMConfigUpdateDTO gcpsmConfigUpdateDTO = GCPSMConfigUpdateDTO.builder()
                                                    .delegateSelectors(gcpSMConnectorDTO.getDelegateSelectors())
                                                    .isDefault(gcpSMConnectorDTO.isDefault())

                                                    .name(connector.getName())
                                                    .encryptionType(EncryptionType.GCP_SECRETS_MANAGER)
                                                    .tags(connector.getTags())
                                                    .description(connector.getDescription())
                                                    .build();

    if (null != gcpSMConnectorDTO.getCredentials().getDecryptedValue()) {
      gcpsmConfigUpdateDTO.setCredentials(gcpSMConnectorDTO.getCredentials().getDecryptedValue());
    }
    return gcpsmConfigUpdateDTO;
  }
}
