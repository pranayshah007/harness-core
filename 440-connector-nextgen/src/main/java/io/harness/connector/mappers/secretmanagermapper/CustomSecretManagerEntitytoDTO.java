/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.secretmanagermapper;

import io.harness.connector.entities.embedded.customsecretmanager.CustomSecretManagerConnector;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.customseceretmanager.CustomSecretManagerConnectorDTO;
import io.harness.encryption.SecretRefHelper;

public class CustomSecretManagerEntitytoDTO
    implements ConnectorEntityToDTOMapper<CustomSecretManagerConnectorDTO, CustomSecretManagerConnector> {
  @Override
  public CustomSecretManagerConnectorDTO createConnectorDTO(CustomSecretManagerConnector connector) {
    return CustomSecretManagerConnectorDTO.builder()
        .template(connector.getTemplate())
        .connectorRef(SecretRefHelper.createSecretRef(connector.getConnectorRef()))
        .isDefault(connector.isDefault())
        .host(connector.getHost())
        .workingDirectory(connector.getWorkingDirectory())
        .executeOnDelegate(connector.isOnDelegate())
        .delegateSelectors(connector.getDelegateSelectors())
        .build();
  }
}