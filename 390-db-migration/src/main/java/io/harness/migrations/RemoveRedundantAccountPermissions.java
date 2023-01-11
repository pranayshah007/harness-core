/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.MongoUtils.setUnset;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_IP_WHITELISTING;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_USER_AND_USER_GROUPS_AND_API_KEYS;
import static software.wings.security.PermissionAttribute.PermissionType.VIEW_USER_AND_USER_GROUPS_AND_API_KEYS;

import io.harness.persistence.HIterator;

import software.wings.beans.security.AccountPermissions;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.security.PermissionAttribute;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoveRedundantAccountPermissions implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  private void runMigration() {
    try (HIterator<UserGroup> userGroupHIterator = new HIterator<>(
             wingsPersistence.createQuery(UserGroup.class).field("accountPermissions").exists().fetch())) {
      while (userGroupHIterator.hasNext()) {
        UserGroup userGroup = userGroupHIterator.next();
        if (checkIfUserGroupHasAccountPermissions(userGroup)) {
          Set<PermissionAttribute.PermissionType> accountPermissions =
              userGroup.getAccountPermissions().getPermissions();

          accountPermissions.remove(MANAGE_USER_AND_USER_GROUPS_AND_API_KEYS);
          accountPermissions.remove(VIEW_USER_AND_USER_GROUPS_AND_API_KEYS);
          accountPermissions.remove(MANAGE_IP_WHITELISTING);

          UpdateOperations<UserGroup> operations = wingsPersistence.createUpdateOperations(UserGroup.class);
          setUnset(
              operations, "accountPermissions", AccountPermissions.builder().permissions(accountPermissions).build());
          wingsPersistence.update(userGroup, operations);
        }
      }
    }
  }

  private boolean checkIfUserGroupHasAccountPermissions(UserGroup userGroup) {
    return userGroup.getAccountPermissions() != null && isNotEmpty(userGroup.getAccountPermissions().getPermissions());
  }

  @Override
  public void migrate() {
    try {
      runMigration();
    } catch (Exception e) {
      log.error("Migration: Error on running migration", e);
    }
  }
}
