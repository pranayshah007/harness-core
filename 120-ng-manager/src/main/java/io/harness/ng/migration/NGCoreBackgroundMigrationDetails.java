/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.migration;

import io.harness.migration.MigrationDetails;
import io.harness.migration.NGMigration;
import io.harness.migration.beans.MigrationType;
import io.harness.migration.utils.NoopMigration;
import io.harness.ng.core.migration.CopyTemplatesPermissionRoleUpdate;
import io.harness.ng.core.migration.NGWebhookMendateSettingsCategoryUpdateMigration;
import io.harness.ng.core.migration.PopulateYamlFieldInNGEnvironmentMigration;
import io.harness.ng.core.migration.background.AddDeploymentTypeToInfrastructureEntityMigration;
import io.harness.ng.core.migration.background.DeleteSoftDeletedConnectorsMigration;
import io.harness.ng.core.migration.background.PopulateYamlAuthFieldInNGJiraConnectorMigration;
import io.harness.ng.core.migration.background.PopulateYamlAuthFieldInNGServiceNowConnectorMigration;
import io.harness.ng.core.migration.background.PopulateYamlFieldInNGServiceEntityMigration;
import io.harness.ng.core.migration.background.UpdateEnvironmentRefValueInServiceOverrideNGMigration;
import io.harness.ng.core.migration.background.UserMetadataTwoFactorAuthenticationMigration;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class NGCoreBackgroundMigrationDetails implements MigrationDetails {
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
        .add(Pair.of(1, AddDeploymentTypeToInfrastructureEntityMigration.class))
        .add(Pair.of(2, NoopMigration.class))
        .add(Pair.of(3, NoopMigration.class))
        .add(Pair.of(4, CopyTemplatesPermissionRoleUpdate.class))
        .add(Pair.of(5, PopulateYamlAuthFieldInNGServiceNowConnectorMigration.class))
        .add(Pair.of(6, NGWebhookMendateSettingsCategoryUpdateMigration.class))
        .add(Pair.of(7, DeleteSoftDeletedConnectorsMigration.class))
        .add(Pair.of(8, PopulateYamlAuthFieldInNGJiraConnectorMigration.class))
        .add(Pair.of(9, NoopMigration.class))
        .add(Pair.of(10, UserMetadataTwoFactorAuthenticationMigration.class))
        .add(Pair.of(11, UpdateEnvironmentRefValueInServiceOverrideNGMigration.class))
        .add(Pair.of(12, PopulateYamlFieldInNGEnvironmentMigration.class))
        .add(Pair.of(13, PopulateYamlFieldInNGServiceEntityMigration.class))
        .build();
  }
}
