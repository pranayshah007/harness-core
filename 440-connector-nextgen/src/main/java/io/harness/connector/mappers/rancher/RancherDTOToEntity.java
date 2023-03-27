/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.rancher;

import io.harness.connector.entities.embedded.rancher.RancherClusterConfig;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.rancher.RancherConnectorDTO;

public class RancherDTOToEntity implements ConnectorDTOToEntityMapper<RancherConnectorDTO, RancherClusterConfig> {
  @Override
  public RancherClusterConfig toConnectorEntity(RancherConnectorDTO connectorDTO) {
    RancherClusterConfig rancherConnector = RancherClusterConfig
                                                .builder()
                                                // TODO convert DTO to connector
                                                .build();
    rancherConnector.setType(ConnectorType.RANCHER);
    return rancherConnector;
  }
}
