/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration;

import io.harness.cvng.migration.timescale.CreateSLOHealthIndicator;
import io.harness.cvng.migration.timescale.CreateSLOHistoryTable;
import io.harness.cvng.migration.timescale.CreateSLOPeriodicSummary;
import io.harness.cvng.migration.timescale.MigrateSLOtoTimeScaleDb;
import io.harness.migration.MigrationDetails;
import io.harness.migration.NGMigration;
import io.harness.migration.beans.MigrationType;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class SRMTimescaleMigrationList implements MigrationDetails {
  @Override
  public MigrationType getMigrationTypeName() {
    return MigrationType.TimeScaleMigration;
  }

  @Override
  public boolean isBackground() {
    return false;
  }

  @Override
  public List<Pair<Integer, Class<? extends NGMigration>>> getMigrations() {
    return new ImmutableList.Builder<Pair<Integer, Class<? extends NGMigration>>>()
        .add(Pair.of(1, CreateSLOHealthIndicator.class))
        .add(Pair.of(2, CreateSLOPeriodicSummary.class))
        .add(Pair.of(3, CreateSLOHistoryTable.class))
        .add(Pair.of(4, MigrateSLOtoTimeScaleDb.class))
        .build();
  }
}
