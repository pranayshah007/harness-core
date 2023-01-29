/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.accountpermission;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.MongoUtils.setUnset;

import static software.wings.security.PermissionAttribute.PermissionType.APPLICATION_CREATE_DELETE;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_APPLICATIONS;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.security.AccountPermissions;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupKeys;
import software.wings.dl.WingsPersistence;
import software.wings.security.PermissionAttribute;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManageApplicationsPermissionMigration implements Migration {
  private final String DEBUG_MESSAGE = "MANAGE_APPLICATIONS: ";

  @Inject private WingsPersistence wingsPersistence;

  private void runMigration() {
    try (HIterator<UserGroup> userGroupHIterator = new HIterator<>(
             wingsPersistence.createQuery(UserGroup.class).field(UserGroupKeys.accountPermissions).exists().fetch())) {
      // Adding MANAGE_APPLICATIONS permission for all user groups with APPLICATION_CREATE_DELETE permission
      while (userGroupHIterator.hasNext()) {
        UserGroup userGroup = userGroupHIterator.next();
        addPermissionToCurrentUserGroup(userGroup);
      }
    } catch (Exception e) {
      log.error(DEBUG_MESSAGE + "Error creating query", e);
    }
  }

  private void addPermissionToCurrentUserGroup(UserGroup userGroup) {
    try {
      if (checkIfUserGroupContainsApplicationCreatePermission(userGroup)) {
        Set<PermissionAttribute.PermissionType> accountPermissions = userGroup.getAccountPermissions().getPermissions();

        accountPermissions.add(MANAGE_APPLICATIONS);

        UpdateOperations<UserGroup> operations = wingsPersistence.createUpdateOperations(UserGroup.class);
        setUnset(operations, UserGroupKeys.accountPermissions,
            AccountPermissions.builder().permissions(accountPermissions).build());
        wingsPersistence.update(userGroup, operations);
      }
    } catch (Exception e) {
      log.error(DEBUG_MESSAGE + "Error occurred for userGroup:[{}]", userGroup.getUuid(), e);
    }
  }

  private boolean checkIfUserGroupContainsApplicationCreatePermission(UserGroup userGroup) {
    return userGroup.getAccountPermissions() != null && isNotEmpty(userGroup.getAccountPermissions().getPermissions())
        && userGroup.getAccountPermissions().getPermissions().contains(APPLICATION_CREATE_DELETE);
  }

  @Override
  public void migrate() {
    log.info(DEBUG_MESSAGE + "Starting migration");
    try {
      runMigration();
    } catch (Exception e) {
      log.error(DEBUG_MESSAGE + "Error occurred while migrating MANAGE_APPLICATIONS", e);
    }
    log.info(DEBUG_MESSAGE + "Completed migration");
  }
}
