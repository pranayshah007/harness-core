/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.splunkconnectormapper;

import io.harness.connector.entities.embedded.splunkconnector.SplunkConnector;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.encryption.SecretRefHelper;

import com.google.inject.Singleton;

@Singleton
public class SplunkDTOToEntity implements ConnectorDTOToEntityMapper<SplunkConnectorDTO, SplunkConnector> {
  @Override
  public SplunkConnector toConnectorEntity(SplunkConnectorDTO connectorDTO) {
    return SplunkConnector.builder()
        .username(connectorDTO.getUsername())
        .passwordRef(SecretRefHelper.getSecretConfigString(connectorDTO.getPasswordRef()))
        .tokenRef(SecretRefHelper.getSecretConfigString(connectorDTO.getTokenRef()))
        .authType(connectorDTO.getAuthType())
        .splunkUrl(connectorDTO.getSplunkUrl())
        .accountId(connectorDTO.getAccountId())
        .build();
  }
}
