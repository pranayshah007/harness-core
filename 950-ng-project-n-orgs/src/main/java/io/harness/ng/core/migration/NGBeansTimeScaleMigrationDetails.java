/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import io.harness.migration.MigrationDetails;
import io.harness.migration.NGMigration;
import io.harness.migration.beans.MigrationType;
import io.harness.ng.core.migration.tasks.CreateExecutionTagsInfoNgTable;
import io.harness.ng.core.migration.tasks.CreateInfrastructureTimeScaleTable;
import io.harness.ng.core.migration.tasks.CreateOrganizationTimeScaleTable;
import io.harness.ng.core.migration.tasks.CreateTagsInfoNgTable;
import io.harness.ng.core.migration.tasks.CreateTagsTimeScaleTable;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class NGBeansTimeScaleMigrationDetails implements MigrationDetails {
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
        .add(Pair.of(1, CreateOrganizationTimeScaleTable.class))
        .add(Pair.of(2, CreateTagsTimeScaleTable.class))
        .add(Pair.of(3, CreateInfrastructureTimeScaleTable.class))
        .add(Pair.of(4, CreateTagsInfoNgTable.class))
        .add(Pair.of(5, CreateExecutionTagsInfoNgTable.class))
        .build();
  }
}
