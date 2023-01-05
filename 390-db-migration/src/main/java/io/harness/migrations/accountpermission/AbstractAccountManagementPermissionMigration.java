/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.accountpermission;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.MongoUtils.setUnset;

import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.security.AccountPermissions;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupKeys;
import software.wings.dl.WingsPersistence;
import software.wings.security.PermissionAttribute.PermissionType;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public abstract class AbstractAccountManagementPermissionMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  private static final String debugMessage = "ACCOUNT_PERMISSION_MIGRATION: ";

  private void runMigration() {
    try (HIterator<UserGroup> userGroupHIterator = new HIterator<>(
             wingsPersistence.createQuery(UserGroup.class).field(UserGroupKeys.accountPermissions).exists().fetch())) {
      while (userGroupHIterator.hasNext()) {
        UserGroup userGroup = userGroupHIterator.next();
        try {
          if (checkIfUserGroupContainsAccountManagementPermission(userGroup)) {
            Set<PermissionType> accountPermissions = userGroup.getAccountPermissions().getPermissions();
            accountPermissions.addAll(getToBeAddedPermissions());

            UpdateOperations<UserGroup> operations = wingsPersistence.createUpdateOperations(UserGroup.class);
            setUnset(operations, UserGroupKeys.accountPermissions,
                AccountPermissions.builder().permissions(accountPermissions).build());
            wingsPersistence.update(userGroup, operations);
          }
        } catch (Exception e) {
          log.error(debugMessage + "Error occurred for userGroup:[{}]", userGroup.getUuid(), e);
        }
      }
    }
  }

  private boolean checkIfUserGroupContainsAccountManagementPermission(UserGroup userGroup) {
    return userGroup.getAccountPermissions() != null && isNotEmpty(userGroup.getAccountPermissions().getPermissions())
        && userGroup.getAccountPermissions().getPermissions().contains(ACCOUNT_MANAGEMENT);
  }

  public abstract Set<PermissionType> getToBeAddedPermissions();

  @Override
  public void migrate() {
    log.info(debugMessage + "Starting Migration");
    try {
      runMigration();
    } catch (Exception e) {
      log.error(debugMessage + "Error on running migration", e);
    }
    log.info(debugMessage + "Completed Migration");
  }
}
