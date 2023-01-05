/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.migrations.OnPrimaryManagerMigration;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Schema;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class MigrationServiceImplTest extends WingsBaseTest {
  @Inject MigrationServiceImpl migrationService;
  @Inject private HPersistence persistence;

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testMigrationsOnPrimary() {
    testMigrationUpgrade();
    testMigrationDowngrade();
  }

  private void testMigrationUpgrade() {
    Schema schema = Schema.builder()
                        .version(0)
                        .backgroundVersion(0)
                        .seedDataVersion(0)
                        .timescaleDbVersion(0)
                        .timescaleDBDataVersion(0)
                        .onPrimaryManagerVersion(0)
                        .build();
    persistence.save(schema);

    Map<Integer, Class<? extends OnPrimaryManagerMigration>> onPrimaryManagerMigrationMap = new HashMap<>();
    onPrimaryManagerMigrationMap.put(1, TestClass.class);
    migrationService.runMigrationWhenNewManagerIsPrimary(1, onPrimaryManagerMigrationMap);
    Query<Schema> query = persistence.createQuery(Schema.class);
    assertThat(query.get().getOnPrimaryManagerVersion()).isEqualTo(1);
  }

  private void testMigrationDowngrade() {
    Schema schema = Schema.builder()
                        .version(0)
                        .backgroundVersion(0)
                        .seedDataVersion(0)
                        .timescaleDbVersion(0)
                        .timescaleDBDataVersion(0)
                        .onPrimaryManagerVersion(2)
                        .build();
    persistence.save(schema);

    Map<Integer, Class<? extends OnPrimaryManagerMigration>> onPrimaryManagerMigrationMap = new HashMap<>();
    onPrimaryManagerMigrationMap.put(1, TestClass.class);
    migrationService.scheduleOnPrimaryMigrations(schema, 1, onPrimaryManagerMigrationMap);
    Query<Schema> query = persistence.createQuery(Schema.class);
    assertThat(query.get().getOnPrimaryManagerVersion()).isEqualTo(1);
  }

  public static class TestClass implements OnPrimaryManagerMigration {
    @Override
    public void migrate() {
      // do nothing.
    }
  }
}
