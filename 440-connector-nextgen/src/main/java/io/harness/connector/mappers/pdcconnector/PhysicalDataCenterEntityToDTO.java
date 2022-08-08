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
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.pdcconnector.HostDTO;
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorDTO;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDP)
@Singleton
public class PhysicalDataCenterEntityToDTO
    implements ConnectorEntityToDTOMapper<PhysicalDataCenterConnectorDTO, PhysicalDataCenterConnector> {
  @Override
  public PhysicalDataCenterConnectorDTO createConnectorDTO(PhysicalDataCenterConnector connector) {
    return PhysicalDataCenterConnectorDTO.builder().hosts(getHostDTOSFromHosts(connector.getHosts())).build();
  }

  private List<HostDTO> getHostDTOSFromHosts(List<Host> hosts) {
    if (isEmpty(hosts)) {
      return Collections.emptyList();
    }

    return hosts.stream().filter(Objects::nonNull).map(this::getHostDTO).collect(Collectors.toList());
  }

  @NotNull
  private HostDTO getHostDTO(Host host) {
    HostDTO hostDTO = new HostDTO();
    hostDTO.setHostName(host.getHostName());
    hostDTO.setHostAttributes(getHostAttributes(host));
    return hostDTO;
  }

  private Map<String, String> getHostAttributes(Host host) {
    if (host.getHostAttributes() == null) {
      return Collections.emptyMap();
    }

    return ImmutableMap.copyOf(host.getHostAttributes());
  }
}
