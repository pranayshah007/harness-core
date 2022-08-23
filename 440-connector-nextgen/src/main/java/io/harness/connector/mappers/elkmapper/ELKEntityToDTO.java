/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.elkmapper;

import io.harness.connector.entities.embedded.elkconnector.ELKConnector;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.elkconnector.ELKAuthType;
import io.harness.delegate.beans.connector.elkconnector.ELKConnectorDTO;
import io.harness.encryption.SecretRefHelper;

public class ELKEntityToDTO implements ConnectorEntityToDTOMapper<ELKConnectorDTO, ELKConnector> {
  @Override
  public ELKConnectorDTO createConnectorDTO(ELKConnector connector) {
    return ELKConnectorDTO.builder()
        .url(connector.getUrl())
        .username(connector.getUsername())
        .passwordRef(SecretRefHelper.createSecretRef(connector.getPasswordRef()))
        .apiKeyId(connector.getApiKeyId())
        .apiKeyRef(SecretRefHelper.createSecretRef(connector.getApiKeyRef()))
        .authType(connector.getAuthType() == null ? ELKAuthType.USERNAME_PASSWORD : connector.getAuthType())
        .build();
  }
}
