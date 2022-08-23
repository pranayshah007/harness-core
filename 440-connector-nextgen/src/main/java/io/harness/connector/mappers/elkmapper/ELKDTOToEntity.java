/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.elkmapper;

import io.harness.connector.entities.embedded.elkconnector.ELKConnector;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.elkconnector.ELKConnectorDTO;
import io.harness.encryption.SecretRefHelper;

public class ELKDTOToEntity implements ConnectorDTOToEntityMapper<ELKConnectorDTO, ELKConnector> {
  @Override
  public ELKConnector toConnectorEntity(ELKConnectorDTO connectorDTO) {
    return ELKConnector.builder()
        .url(connectorDTO.getUrl())
        .username(connectorDTO.getUsername())
        .passwordRef(SecretRefHelper.getSecretConfigString(connectorDTO.getPasswordRef()))
        .apiKeyId(connectorDTO.getApiKeyId())
        .apiKeyRef(SecretRefHelper.getSecretConfigString(connectorDTO.getApiKeyRef()))
        .authType(connectorDTO.getAuthType())
        .build();
  }
}
