/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.prometheusmapper;

import io.harness.connector.entities.embedded.prometheusconnector.PrometheusConnector;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;

public class PrometheusEntityToDTO implements ConnectorEntityToDTOMapper<PrometheusConnectorDTO, PrometheusConnector> {
  @Override
  public PrometheusConnectorDTO createConnectorDTO(PrometheusConnector connector) {
    return PrometheusConnectorDTO.builder().url(connector.getUrl()).build();
  }
}
