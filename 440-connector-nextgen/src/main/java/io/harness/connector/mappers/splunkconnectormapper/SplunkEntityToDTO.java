/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.splunkconnectormapper;

import io.harness.connector.entities.embedded.splunkconnector.SplunkConnector;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.splunkconnector.SplunkAuthType;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.encryption.SecretRefHelper;

public class SplunkEntityToDTO implements ConnectorEntityToDTOMapper<SplunkConnectorDTO, SplunkConnector> {
  @Override
  public SplunkConnectorDTO createConnectorDTO(SplunkConnector connector) {
    SplunkAuthType authType = connector.getAuthType() != null ? connector.getAuthType() : SplunkAuthType.USER_PASSWORD;
    return SplunkConnectorDTO.builder()
        .username(connector.getUsername())
        .passwordRef(SecretRefHelper.createSecretRef(connector.getPasswordRef()))
        .authType(authType)
        .tokenRef(SecretRefHelper.createSecretRef(connector.getTokenRef()))
        .splunkUrl(connector.getSplunkUrl())
        .accountId(connector.getAccountId())
        .build();
  }
}
