/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.commons.migration;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.acl.migration.ACLAddBooleanFieldsMigration;
import io.harness.accesscontrol.resources.resourcegroups.migration.MultipleManagedResourceGroupMigration;
import io.harness.accesscontrol.roleassignments.migration.AccountBasicRoleAssignmentAdditionMigration;
import io.harness.accesscontrol.roleassignments.migration.AccountBasicRoleAssignmentInternalFieldMigration;
import io.harness.accesscontrol.roleassignments.migration.RoleAssignmentPrincipalScopeLevelMigration;
import io.harness.accesscontrol.roleassignments.migration.RoleAssignmentResourceGroupMigration;
import io.harness.accesscontrol.roleassignments.migration.RoleAssignmentScopeAdditionMigration;
import io.harness.accesscontrol.roleassignments.migration.SuperAdminViaAllResourcesMigration;
import io.harness.accesscontrol.roleassignments.migration.UserInfoMigration;
import io.harness.accesscontrol.scopes.harness.migration.ScopeMigration;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.MigrationDetails;
import io.harness.migration.NGMigration;
import io.harness.migration.beans.MigrationType;
import io.harness.migration.utils.NoopMigration;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(PL)
public class AccessControlMongoBackgroundMigrationDetails implements MigrationDetails {
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
        .add(Pair.of(1, RoleAssignmentScopeAdditionMigration.class))
        .add(Pair.of(2, ScopeMigration.class))
        .add(Pair.of(3, RoleAssignmentScopeAdditionMigration.class))
        .add(Pair.of(5, NoopMigration.class))
        .add(Pair.of(6, MultipleManagedResourceGroupMigration.class))
        .add(Pair.of(7, NoopMigration.class))
        .add(Pair.of(8, RoleAssignmentResourceGroupMigration.class))
        .add(Pair.of(9, SuperAdminViaAllResourcesMigration.class))
        .add(Pair.of(10, RoleAssignmentPrincipalScopeLevelMigration.class))
        .add(Pair.of(11, NoopMigration.class))
        .add(Pair.of(12, ACLAddBooleanFieldsMigration.class))
        .add(Pair.of(13, AccountBasicRoleAssignmentAdditionMigration.class))
        .add(Pair.of(14, NoopMigration.class))
        .add(Pair.of(15, NoopMigration.class))
        .add(Pair.of(16, AccountBasicRoleAssignmentInternalFieldMigration.class))
        .add(Pair.of(17, NoopMigration.class))
        .add(Pair.of(18, UserInfoMigration.class))
        .add(Pair.of(19, NoopMigration.class))
        .build();
  }
}
