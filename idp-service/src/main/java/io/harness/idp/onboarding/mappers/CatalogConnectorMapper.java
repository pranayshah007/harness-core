/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.onboarding.entities.CatalogConnector;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class CatalogConnectorMapper {
  public CatalogConnectorInfo toDTO(CatalogConnector catalogConnector) {
    CatalogConnectorInfo catalogConnectorInfo = new CatalogConnectorInfo();
    catalogConnectorInfo.setInfraConnector(catalogConnector.getInfraConnector());
    catalogConnectorInfo.setSourceConnector(catalogConnector.getSourceConnector());
    catalogConnectorInfo.setRepo(catalogConnector.getCatalogRepositoryDetails().getRepo());
    catalogConnectorInfo.setBranch(catalogConnector.getCatalogRepositoryDetails().getBranch());
    catalogConnectorInfo.setPath(catalogConnector.getCatalogRepositoryDetails().getPath());
    return catalogConnectorInfo;
  }
}
