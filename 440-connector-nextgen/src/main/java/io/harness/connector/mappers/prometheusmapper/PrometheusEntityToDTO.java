/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.prometheusmapper;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.connector.entities.embedded.prometheusconnector.PrometheusConnector;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthKeyAndValue;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO.PrometheusConnectorDTOBuilder;
import io.harness.encryption.SecretRefHelper;

import java.util.stream.Collectors;

public class PrometheusEntityToDTO implements ConnectorEntityToDTOMapper<PrometheusConnectorDTO, PrometheusConnector> {
  @Override
  public PrometheusConnectorDTO createConnectorDTO(PrometheusConnector connector) {
    PrometheusConnectorDTOBuilder prometheusConnectorDTOBuilder =
        PrometheusConnectorDTO.builder()
            .url(connector.getUrl())
            .headers(connector.getHeaders()
                         .stream()
                         .map(header -> {
                           return CustomHealthKeyAndValue.builder()
                               .key(header.getKey())
                               .value(header.getValue())
                               .isValueEncrypted(header.isValueEncrypted())
                               .encryptedValueRef(SecretRefHelper.createSecretRef(header.getEncryptedValueRef()))
                               .build();
                         })
                         .collect(Collectors.toList()));
    if (isNotEmpty(connector.getUsername()) && isNotEmpty(connector.getPasswordRef())) {
      prometheusConnectorDTOBuilder = prometheusConnectorDTOBuilder.username(connector.getUsername())
                                          .passwordRef(SecretRefHelper.createSecretRef(connector.getPasswordRef()));
    }

    return prometheusConnectorDTOBuilder.build();
  }
}
