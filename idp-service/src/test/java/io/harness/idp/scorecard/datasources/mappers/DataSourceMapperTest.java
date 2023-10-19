/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datasources.mappers;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.datasources.beans.entity.DataSourceEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.DataSource;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class DataSourceMapperTest {
  private static final String TEST_DATA_SOURCE_IDENTIFIER = " test-datasource-identifier";
  private static final String TEST_DATA_SOURCE_NAME = "test-datasource-name";
  private static final String TEST_DATA_SOURCE_DESCRIPTION = "test-datasource-description";
  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToDTO() {
    DataSourceEntity dataSourceEntity = DataSourceEntity.builder()
                                            .name(TEST_DATA_SOURCE_NAME)
                                            .identifier(TEST_DATA_SOURCE_IDENTIFIER)
                                            .description(TEST_DATA_SOURCE_DESCRIPTION)
                                            .build();

    DataSource dataSource = DataSourceMapper.toDTO(dataSourceEntity);
    assertEquals(dataSourceEntity.getName(), dataSource.getName());
    assertEquals(dataSourceEntity.getIdentifier(), dataSource.getIdentifier());
    assertEquals(dataSourceEntity.getDescription(), dataSource.getDescription());
  }
}
