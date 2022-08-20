/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.customseceretmanager.CustomSecretManagerConnectorDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.secretmanagerclient.dto.CustomSecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.CustomSecretManagerConfigDTO.CustomSecretManagerConfigDTOBuilder;
import io.harness.security.encryption.EncryptionType;

public class CustomSecretManagerConfigDTOMapper {
  public static CustomSecretManagerConfigDTO getCustomSecretManagerConfigDTO(
      String accountIdentifier, ConnectorDTO connectorRequestDTO, CustomSecretManagerConnectorDTO connectorDTO) {
    ConnectorInfoDTO connector = connectorRequestDTO.getConnectorInfo();
    CustomSecretManagerConfigDTOBuilder<?, ?> builder =
        CustomSecretManagerConfigDTO.builder()
            .isDefault(false)
            .encryptionType(EncryptionType.CUSTOM_NG)

            .name(connector.getName())
            .accountIdentifier(accountIdentifier)
            .orgIdentifier(connector.getOrgIdentifier())
            .projectIdentifier(connector.getProjectIdentifier())
            .tags(connector.getTags())
            .identifier(connector.getIdentifier())
            .description(connector.getDescription())
            .harnessManaged(connectorDTO.isHarnessManaged())

            .delegateSelectors(connectorDTO.getDelegateSelectors())
            .onDelegate(connectorDTO.getExecuteOnDelegate())
            .connectorRef(SecretRefHelper.getSecretConfigString(connectorDTO.getConnectorRef()))
            .host(connectorDTO.getHost())
            .workingDirectory(connectorDTO.getWorkingDirectory())
            .template(connectorDTO.getTemplate());

    if (null != connectorDTO.getConnectorRef() && null != connectorDTO.getConnectorRef().getDecryptedValue()) {
      builder.connectorRef(String.valueOf(connectorDTO.getConnectorRef().getDecryptedValue()));
    }
    return builder.build();
  }
}
