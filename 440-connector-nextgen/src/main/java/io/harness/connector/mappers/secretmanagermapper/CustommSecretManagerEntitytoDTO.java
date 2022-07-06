/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.secretmanagermapper;

import io.harness.connector.entities.embedded.customsecretmanager.CustomSecretManagerConnector;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.customseceretmanager.CustomSecretManagerDTO;

public class CustommSecretManagerEntitytoDTO
    implements ConnectorEntityToDTOMapper<CustomSecretManagerDTO, CustomSecretManagerConnector> {
  @Override
  public CustomSecretManagerDTO createConnectorDTO(CustomSecretManagerConnector connector) {
    return CustomSecretManagerDTO.builder()
        .templateRef(connector.getTemplateRef())
        .connectorRef(connector.getConnectorRef())
        .host(connector.getHost())
        .onDelegate(connector.isOnDelegate())
        .delegateSelectors(connector.getDelegateSelectors())
        .testVariables(connector.getTestVariables())
        .build();
  }
}
