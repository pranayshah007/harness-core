/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import io.harness.migration.MigrationDetails;
import io.harness.migration.NGMigration;
import io.harness.migration.beans.MigrationType;
import io.harness.ng.core.migration.tasks.parentid.AddParentIdToOrganizationMigration;
import io.harness.ng.core.migration.tasks.parentid.AddParentIdToProjectMigration;
import io.harness.ng.core.migration.tasks.uniqueid.AddUniqueIdToOrganizationMigration;
import io.harness.ng.core.migration.tasks.uniqueid.AddUniqueIdToProjectMigration;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class UniqueIdParentIdBackgroundMigrationDetails implements MigrationDetails {
  @Override
  public MigrationType getMigrationTypeName() {
    return MigrationType.MongoBGMigration;
  }

  @Override
  public boolean isBackground() {
    return true;
  }

  @Override
  public List<Pair<Integer, Class<? extends NGMigration>>> getMigrations() {
    return new ImmutableList.Builder<Pair<Integer, Class<? extends NGMigration>>>()
        .add(Pair.of(1, AddUniqueIdToOrganizationMigration.class))
        .add(Pair.of(2, AddParentIdToOrganizationMigration.class))
        .add(Pair.of(3, AddUniqueIdToProjectMigration.class))
        .add(Pair.of(4, AddParentIdToProjectMigration.class))
        .build();
  }
}
