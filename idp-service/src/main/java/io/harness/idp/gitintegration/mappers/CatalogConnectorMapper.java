/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;
import io.harness.spec.server.idp.v1.model.ConnectorDetails;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class CatalogConnectorMapper {
  public CatalogConnectorInfo toDTO(CatalogConnectorEntity catalogConnectorEntity) {
    CatalogConnectorInfo catalogConnectorInfo = new CatalogConnectorInfo();
    ConnectorDetails connectorDetails = new ConnectorDetails();
    connectorDetails.setIdentifier(catalogConnectorEntity.getConnectorIdentifier());
    connectorDetails.setType(ConnectorDetails.TypeEnum.valueOf(catalogConnectorEntity.getConnectorProviderType()));
    catalogConnectorInfo.setInfraConnector(connectorDetails);
    catalogConnectorInfo.setRepo(catalogConnectorEntity.getCatalogRepositoryDetails().getRepo());
    catalogConnectorInfo.setBranch(catalogConnectorEntity.getCatalogRepositoryDetails().getBranch());
    catalogConnectorInfo.setPath(catalogConnectorEntity.getCatalogRepositoryDetails().getPath());
    return catalogConnectorInfo;
  }
}
