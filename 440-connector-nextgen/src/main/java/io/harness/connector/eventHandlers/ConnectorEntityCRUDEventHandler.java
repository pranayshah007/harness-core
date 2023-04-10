/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.eventHandlers;

import static io.harness.ConnectorConstants.CONNECTOR_DECORATOR_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.services.ConnectorService;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.gitsync.persistance.EntityKeySource;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

@OwnedBy(DX)
@Singleton
@Slf4j
public class ConnectorEntityCRUDEventHandler {
  ConnectorService connectorService;
  EntityKeySource entityKeySource;

  private static final int PAGE_SIZE = 10;

  @Inject
  public ConnectorEntityCRUDEventHandler(
      @Named(CONNECTOR_DECORATOR_SERVICE) ConnectorService connectorService, EntityKeySource entityKeySource) {
    this.connectorService = connectorService;
    this.entityKeySource = entityKeySource;
  }

  public boolean deleteAssociatedConnectors(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    List<ConnectorResponseDTO> connectorResponseDTOS =
        fetchAllConnectorInGivenScope(accountIdentifier, orgIdentifier, projectIdentifier);
    List<String> connectorIdentifiers = connectorResponseDTOS.stream()
                                            .map(ConnectorResponseDTO::getConnector)
                                            .map(ConnectorInfoDTO::getIdentifier)
                                            .collect(Collectors.toList());
    if (!connectorIdentifiers.isEmpty()) {
      final EntityScopeInfo.Builder entityScopeInfoBuilder =
          EntityScopeInfo.newBuilder().setAccountId(accountIdentifier);
      if (!isEmpty(projectIdentifier)) {
        entityScopeInfoBuilder.setProjectId(StringValue.of(projectIdentifier));
      }
      if (!isEmpty(orgIdentifier)) {
        entityScopeInfoBuilder.setOrgId(StringValue.of(orgIdentifier));
      }
      entityKeySource.updateKey(entityScopeInfoBuilder.build());
      try {
        connectorService.deleteBatch(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifiers);
      } catch (Exception ex) {
        log.error("Exception occurred in delete batch call", ex);
      }
    }
    return true;
  }

  private List<ConnectorResponseDTO> fetchAllConnectorInGivenScope(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Page<ConnectorResponseDTO> pagedConnectorList = null;
    List<ConnectorResponseDTO> connectorList = new ArrayList<>();
    do {
      pagedConnectorList =
          connectorService.list(accountIdentifier, null, orgIdentifier, projectIdentifier, null, null, false, false,
              PageUtils.getPageRequest(pagedConnectorList == null ? 0 : (pagedConnectorList.getNumber() + 1), PAGE_SIZE,
                  List.of(ConnectorKeys.lastModifiedAt, Sort.Direction.DESC.toString())));
      connectorList.addAll(pagedConnectorList.stream().collect(Collectors.toList()));
    } while (pagedConnectorList.hasNext());
    return connectorList;
  }
}
