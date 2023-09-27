/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.MigrationDetails;
import io.harness.migration.NGMigration;
import io.harness.migration.beans.MigrationType;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.IDP)
public class IdpBGMigrationDetails implements MigrationDetails {
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
        .add(Pair.of(1, PluginInfoMigration.class))
        .add(Pair.of(2, PluginInfoMigration.class))
        .add(Pair.of(3, PluginInfoMigration.class))
        .add(Pair.of(4, PluginInfoMigration.class))
        .add(Pair.of(5, PluginInfoMigration.class))
        .add(Pair.of(6, PluginInfoMigration.class))
        .add(Pair.of(7, BackstageEnvSecretIsDeletedMigration.class))
        .add(Pair.of(8, PluginInfoMigration.class))
        .add(Pair.of(9, ScorecardMigration.class))
        .add(Pair.of(10, UserEventEntityUserGroupIdentifierMigration.class))
        .add(Pair.of(11, ScorecardMigration.class))
        .add(Pair.of(12, NameSpaceEntityNextIterationMigration.class))
        .add(Pair.of(13, ScorecardMigration.class))
        .add(Pair.of(14, ScorecardMigration.class))
        .add(Pair.of(15, ScorecardMigration.class))
        .add(Pair.of(16, NameSpaceEntityNextIterationMigration.class))
        .add(Pair.of(17, ScorecardMigration.class))
        .add(Pair.of(18, ScorecardMigration.class))
        .add(Pair.of(19, ScorecardMigration.class))
        .add(Pair.of(20, ScorecardMigration.class))
        .add(Pair.of(21, ScorecardMigration.class))
        .add(Pair.of(22, PluginInfoMigration.class))
        .add(Pair.of(23, ScorecardMigration.class))
        .add(Pair.of(24, ScorecardMigration.class))
        .add(Pair.of(25, PluginInfoMigration.class))
        .add(Pair.of(26, PluginInfoMigration.class))
        .build();
  }
}
