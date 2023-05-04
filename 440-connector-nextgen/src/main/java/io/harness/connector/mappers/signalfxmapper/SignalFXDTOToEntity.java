/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.signalfxmapper;

import io.harness.connector.entities.embedded.signalfxconnector.SignalFXConnector;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.signalfxconnector.SignalFXConnectorDTO;
import io.harness.encryption.SecretRefHelper;

public class SignalFXDTOToEntity implements ConnectorDTOToEntityMapper<SignalFXConnectorDTO, SignalFXConnector> {
  @Override
  public SignalFXConnector toConnectorEntity(SignalFXConnectorDTO connectorDTO) {
    return SignalFXConnector.builder()
        .url(connectorDTO.getUrl())
        .apiTokenRef(SecretRefHelper.getSecretConfigString(connectorDTO.getApiTokenRef()))
        .build();
  }
}
