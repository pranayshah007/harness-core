/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.validation.Validator.notNullCheck;

import static dev.morphia.mapping.Mapper.ID_KEY;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.security.PermissionAttribute.PermissionType;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserPermissionReadMigration implements Migration {
  @Inject WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("Running the UserPermissionReadMigration script.");

    UpdateOperations<UserGroup> operations = wingsPersistence.createUpdateOperations(UserGroup.class);
    try (HIterator<UserGroup> userGroupIterator =
             new HIterator<>(wingsPersistence.createQuery(UserGroup.class, excludeAuthority).fetch())) {
      while (userGroupIterator.hasNext()) {
        UserGroup userGroup = userGroupIterator.next();
        if (null != userGroup.getAccountPermissions()) {
          Set<PermissionType> permissions = userGroup.getAccountPermissions().getPermissions();
          if (null != permissions) {
            if (permissions.contains(PermissionType.USER_PERMISSION_MANAGEMENT)) {
              permissions.add(PermissionType.USER_PERMISSION_READ);
              setUnset(operations, "accountPermissions", userGroup.getAccountPermissions());
              update(userGroup, operations);
            }
          }
        }
      }
    } catch (Exception ex) {
      log.error("UserPermissionReadMigration failed.", ex);
    }
  }

  private void update(UserGroup userGroup, UpdateOperations<UserGroup> operations) {
    notNullCheck("uuid", userGroup.getUuid());
    notNullCheck(UserGroup.ACCOUNT_ID_KEY, userGroup.getAccountId());
    Query<UserGroup> query = wingsPersistence.createQuery(UserGroup.class)
                                 .filter(ID_KEY, userGroup.getUuid())
                                 .filter(UserGroup.ACCOUNT_ID_KEY, userGroup.getAccountId());
    wingsPersistence.update(query, operations);
  }
}
