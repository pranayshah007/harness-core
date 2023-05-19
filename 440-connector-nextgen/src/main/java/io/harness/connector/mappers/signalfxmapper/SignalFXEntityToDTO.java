/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.signalfxmapper;

import io.harness.connector.entities.embedded.signalfxconnector.SignalFXConnector;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.signalfxconnector.SignalFXConnectorDTO;
import io.harness.encryption.SecretRefHelper;

public class SignalFXEntityToDTO implements ConnectorEntityToDTOMapper<SignalFXConnectorDTO, SignalFXConnector> {
  @Override
  public SignalFXConnectorDTO createConnectorDTO(SignalFXConnector connector) {
    return SignalFXConnectorDTO.builder()
        .url(connector.getUrl())
        .apiTokenRef(SecretRefHelper.createSecretRef(connector.getApiTokenRef()))
        .build();
  }
}
