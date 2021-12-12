package io.harness.accesscontrol.commons.migration;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.resources.resourcegroups.migration.MultipleManagedResourceGroupMigration;
import io.harness.accesscontrol.roleassignments.migration.PrivilegedRoleAssignmentMigration;
import io.harness.accesscontrol.roleassignments.migration.RoleAssignmentResourceGroupMigration;
import io.harness.accesscontrol.roleassignments.migration.RoleAssignmentScopeAdditionMigration;
import io.harness.accesscontrol.scopes.harness.migration.ScopeMigration;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.MigrationDetails;
import io.harness.migration.NGMigration;
import io.harness.migration.beans.MigrationType;

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
        .add(Pair.of(5, PrivilegedRoleAssignmentMigration.class))
        .add(Pair.of(6, MultipleManagedResourceGroupMigration.class))
        .add(Pair.of(7, RoleAssignmentResourceGroupMigration.class))
        .build();
  }
}