/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.mappers;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.gitintegration.beans.CatalogRepositoryDetails;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;
import io.harness.spec.server.idp.v1.model.ConnectorDetails.TypeEnum;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class CatalogConnectorMapperTest extends CategoryTest {
  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testToDTO() {
    CatalogRepositoryDetails catalogRepositoryDetails =
        CatalogRepositoryDetails.builder().repo("testRepo").branch("testBranch").path("testPath").build();
    CatalogConnectorEntity catalogConnectorEntity = CatalogConnectorEntity.builder()
                                                        .identifier("testIdentifier")
                                                        .connectorProviderType("GITHUB")
                                                        .catalogRepositoryDetails(catalogRepositoryDetails)
                                                        .build();

    CatalogConnectorInfo catalogConnector = CatalogConnectorMapper.toDTO(catalogConnectorEntity);

    assertEquals(catalogConnectorEntity.getCatalogRepositoryDetails().getRepo(), catalogConnector.getRepo());
    assertEquals(catalogConnectorEntity.getCatalogRepositoryDetails().getBranch(), catalogConnector.getBranch());
    assertEquals(catalogConnectorEntity.getCatalogRepositoryDetails().getPath(), catalogConnector.getPath());
    assertEquals(catalogConnectorEntity.getConnectorIdentifier(), catalogConnector.getConnector().getIdentifier());
  }
}
