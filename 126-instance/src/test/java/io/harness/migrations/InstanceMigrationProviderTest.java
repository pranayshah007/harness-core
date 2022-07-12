/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.migration.MigrationDetails;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class InstanceMigrationProviderTest extends InstancesTestBase {
  @InjectMocks InstanceMigrationProvider instanceMigrationProvider;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getServiceNameTest() {
    assertThat(instanceMigrationProvider.getServiceName()).isEqualTo("instance");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getSchemaClassTest() {
    assertThat(instanceMigrationProvider.getSchemaClass()).isEqualTo(NGInstanceSchema.class);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getMigrationDetailsListTest() {
    List<Class<? extends MigrationDetails>> response = instanceMigrationProvider.getMigrationDetailsList();
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.get(0)).isEqualTo(InstanceStatsTimeScaleMigrationDetails.class);
  }
}
