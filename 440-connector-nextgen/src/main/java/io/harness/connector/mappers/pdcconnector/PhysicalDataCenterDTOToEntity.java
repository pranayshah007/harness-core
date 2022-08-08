/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.pdcconnector;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.pdcconnector.Host;
import io.harness.connector.entities.embedded.pdcconnector.PhysicalDataCenterConnector;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.pdcconnector.HostDTO;
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorDTO;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@OwnedBy(CDP)
@Singleton
public class PhysicalDataCenterDTOToEntity
    implements ConnectorDTOToEntityMapper<PhysicalDataCenterConnectorDTO, PhysicalDataCenterConnector> {
  @Override
  public PhysicalDataCenterConnector toConnectorEntity(PhysicalDataCenterConnectorDTO connectorDTO) {
    return PhysicalDataCenterConnector.builder().hosts(getHostsFromHostDTOs(connectorDTO.getHosts())).build();
  }

  private List<Host> getHostsFromHostDTOs(List<HostDTO> hostDTOs) {
    if (isEmpty(hostDTOs)) {
      return Collections.emptyList();
    }

    return hostDTOs.stream()
        .filter(Objects::nonNull)
        .map(hostDTO
            -> Host.builder().hostName(hostDTO.getHostName()).hostAttributes(getHostAttributes(hostDTO)).build())
        .collect(Collectors.toList());
  }

  private Map<String, String> getHostAttributes(HostDTO hostDTO) {
    if (hostDTO.getHostAttributes() == null) {
      return Collections.emptyMap();
    }

    return ImmutableMap.copyOf(hostDTO.getHostAttributes());
  }
}
