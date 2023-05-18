/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.Constants;
import io.harness.idp.gitintegration.beans.CatalogInfraConnectorType;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.spec.server.idp.v1.model.ConnectorDetails;
import io.harness.spec.server.idp.v1.model.ConnectorInfoResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class ConnectorDetailsMapper {
  public ConnectorDetails toDTO(CatalogConnectorEntity catalogConnectorEntity) {
    ConnectorDetails connectorDetails = new ConnectorDetails();
    connectorDetails.setIdentifier(catalogConnectorEntity.getConnectorIdentifier());
    connectorDetails.setType(
        ConnectorDetails.TypeEnum.valueOf(catalogConnectorEntity.getConnectorProviderType().toUpperCase()));
    return connectorDetails;
  }

  public CatalogConnectorEntity fromDTO(String identifier, String accountIdentifier, String connectorProviderType,
      Set<String> delegateSelectors, String host, String infraConnectorType) {
    return CatalogConnectorEntity.builder()
        .identifier(Constants.IDP_PREFIX + identifier)
        .accountIdentifier(accountIdentifier)
        .connectorIdentifier(identifier)
        .connectorProviderType(connectorProviderType)
        .type(CatalogInfraConnectorType.valueOf(infraConnectorType))
        .delegateSelectors(delegateSelectors)
        .host(host)
        .build();
  }

  public ConnectorInfoResponse toResponse(CatalogConnectorEntity catalogConnectorEntity) {
    ConnectorInfoResponse connectorInfoResponse = new ConnectorInfoResponse();
    connectorInfoResponse.setConnectorDetails(toDTO(catalogConnectorEntity));
    return connectorInfoResponse;
  }

  public List<ConnectorInfoResponse> toResponseList(List<CatalogConnectorEntity> catalogConnectorEntities) {
    List<ConnectorInfoResponse> response = new ArrayList<>();
    catalogConnectorEntities.forEach(
        catalogConnector -> response.add(new ConnectorInfoResponse().connectorDetails(toDTO(catalogConnector))));
    return response;
  }
}
