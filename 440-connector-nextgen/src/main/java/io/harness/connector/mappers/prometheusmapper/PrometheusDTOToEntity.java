/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.prometheusmapper;

import io.harness.connector.entities.embedded.customhealthconnector.CustomHealthConnectorKeyAndValue;
import io.harness.connector.entities.embedded.prometheusconnector.PrometheusConnector;
import io.harness.connector.entities.embedded.prometheusconnector.PrometheusConnector.PrometheusConnectorBuilder;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;
import io.harness.encryption.SecretRefHelper;

import java.util.stream.Collectors;

public class PrometheusDTOToEntity implements ConnectorDTOToEntityMapper<PrometheusConnectorDTO, PrometheusConnector> {
  @Override
  public PrometheusConnector toConnectorEntity(PrometheusConnectorDTO connectorDTO) {
    PrometheusConnectorBuilder prometheusConnectorBuilder =
        PrometheusConnector.builder()
            .url(connectorDTO.getUrl())
            .username(connectorDTO.getUsername())
            .headers(
                connectorDTO.getHeaders()
                    .stream()
                    .map(header
                        -> CustomHealthConnectorKeyAndValue.builder()
                               .key(header.getKey())
                               .value(header.getValue())
                               .isValueEncrypted(header.isValueEncrypted())
                               .encryptedValueRef(SecretRefHelper.getSecretConfigString(header.getEncryptedValueRef()))
                               .build())
                    .collect(Collectors.toList()));
    if (connectorDTO.getPasswordRef() != null) {
      prometheusConnectorBuilder =
          prometheusConnectorBuilder.passwordRef(SecretRefHelper.getSecretConfigString(connectorDTO.getPasswordRef()));
    }

    return prometheusConnectorBuilder.build();
  }
}
