/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.utils.PageUtils.getPageRequest;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.connector.entities.Connector;
import io.harness.connector.expression.HostFilterFunctor;
import io.harness.connector.mappers.ConnectorMapper;
import io.harness.connector.services.NGHostService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.pdcconnector.HostDTO;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterDTO;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.repositories.ConnectorRepository;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class NGHostServiceImpl implements NGHostService {
  private final HostFilterFunctor hostFilterFunctor;
  private final ConnectorMapper connectorMapper;
  private final ConnectorRepository connectorRepository;
  private final ConnectorErrorMessagesHelper connectorErrorMessagesHelper;

  @Override
  public Page<HostDTO> filterHostsByConnector(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String scopedConnectorIdentifier, HostFilterDTO filter, PageRequest pageRequest) {
    Connector connector = getConnector(accountIdentifier, orgIdentifier, projectIdentifier, scopedConnectorIdentifier);

    if (!ConnectorType.PDC.equals(connector.getType())) {
      throw new InvalidRequestException("Filtering of hosts is supported only for PDC type.");
    }

    PhysicalDataCenterConnectorDTO connectorDTO =
        (PhysicalDataCenterConnectorDTO) connectorMapper.getConnectorInfoDTO(connector).getConnectorConfig();

    List<HostDTO> hosts = applyFilter(connectorDTO.getHosts(), filter);

    List<HostDTO> pageResponse = hosts.stream()
                                     .skip((long) pageRequest.getPageIndex() * pageRequest.getPageSize())
                                     .limit(pageRequest.getPageSize())
                                     .sorted(Comparator.comparing(HostDTO::getHostName))
                                     .collect(toList());
    return new PageImpl<>(pageResponse, getPageRequest(pageRequest), hosts.size());
  }

  private Connector getConnector(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String scopedConnectorIdentifier) {
    IdentifierRef connectorIdentifierRef = IdentifierRefHelper.getIdentifierRef(
        scopedConnectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
    String fullyQualifiedIdentifier = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        connectorIdentifierRef.getAccountIdentifier(), connectorIdentifierRef.getOrgIdentifier(),
        connectorIdentifierRef.getProjectIdentifier(), connectorIdentifierRef.getIdentifier());

    return connectorRepository
        .findByFullyQualifiedIdentifierAndDeletedNot(
            fullyQualifiedIdentifier, projectIdentifier, orgIdentifier, accountIdentifier, true)
        .orElseThrow(()
                         -> new InvalidRequestException(connectorErrorMessagesHelper.createConnectorNotFoundMessage(
                             accountIdentifier, orgIdentifier, projectIdentifier, scopedConnectorIdentifier)));
  }

  private List<HostDTO> applyFilter(List<HostDTO> hosts, HostFilterDTO filter) {
    if (isEmpty(hosts)) {
      return Collections.emptyList();
    }

    if (filter == null || isEmpty(filter.getFilter())) {
      return hosts;
    }

    if (HostFilterType.HOST_NAMES.equals(filter.getType())) {
      return hosts.stream()
          .filter(host -> hostFilterFunctor.filterByHostName(filter.getFilter(), host))
          .collect(toList());
    } else if (HostFilterType.HOST_ATTRIBUTES.equals(filter.getType())) {
      return hosts.stream()
          .filter(host -> hostFilterFunctor.filterByHostAttributes(filter.getFilter(), host))
          .collect(toList());
    }

    return hosts;
  }
}
