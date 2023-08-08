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
import io.harness.migration.NGMigration;
import io.harness.migration.beans.MigrationType;
import io.harness.migrations.timescale.CreateInstanceStatsDayTable;
import io.harness.migrations.timescale.CreateInstanceStatsHourTable;
import io.harness.migrations.timescale.CreateInstanceStatsIteratorTable;
import io.harness.migrations.timescale.CreateInstanceStatsTable;
import io.harness.migrations.timescale.InitTriggerFunctions;
import io.harness.rule.Owner;

import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class InstanceStatsTimeScaleMigrationDetailsTest extends InstancesTestBase {
  @InjectMocks InstanceStatsTimeScaleMigrationDetails instanceStatsTimeScaleMigrationDetails;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getMigrationTypeNameTest() {
    assertThat(instanceStatsTimeScaleMigrationDetails.getMigrationTypeName())
        .isEqualTo(MigrationType.TimeScaleMigration);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void isBackgroundTest() {
    assertThat(instanceStatsTimeScaleMigrationDetails.isBackground()).isEqualTo(false);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getMigrationsTest() {
    List<Pair<Integer, Class<? extends NGMigration>>> response = instanceStatsTimeScaleMigrationDetails.getMigrations();
    assertThat(response.size()).isEqualTo(5);
    assertThat(response.contains(Pair.of(1, InitTriggerFunctions.class))).isTrue();
    assertThat(response.contains(Pair.of(2, CreateInstanceStatsTable.class))).isTrue();
    assertThat(response.contains(Pair.of(3, CreateInstanceStatsHourTable.class))).isTrue();
    assertThat(response.contains(Pair.of(4, CreateInstanceStatsDayTable.class))).isTrue();
    assertThat(response.contains(Pair.of(5, CreateInstanceStatsIteratorTable.class))).isTrue();
  }
}
