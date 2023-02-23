/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.persistence.HQuery.excludeAuthority;

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

@Slf4j
public class AddCEPermissionToAllUserGroups implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("Starting updating admin user groups with CE_ADMIN permission type");
    UserGroup userGroup = null;
    try (HIterator<UserGroup> userGroups =
             new HIterator<>(wingsPersistence.createQuery(UserGroup.class, excludeAuthority)
                                 .field(UserGroupKeys.accountPermissions)
                                 .exists()
                                 .fetch())) {
      while (userGroups.hasNext()) {
        try {
          userGroup = userGroups.next();
          AccountPermissions accountPermissions = userGroup.getAccountPermissions();
          Set<PermissionType> accountPermissionsSet = accountPermissions.getPermissions();
          if (isNotEmpty(accountPermissionsSet)) {
            accountPermissionsSet.add(PermissionType.CE_VIEWER);
            if (accountPermissionsSet.contains(PermissionType.ACCOUNT_MANAGEMENT)) {
              accountPermissionsSet.add(PermissionType.CE_ADMIN);
            }
            UpdateOperations<UserGroup> operations = wingsPersistence.createUpdateOperations(UserGroup.class);
            setUnset(operations, UserGroupKeys.accountPermissions,
                AccountPermissions.builder().permissions(accountPermissionsSet).build());
            wingsPersistence.update(userGroup, operations);
          }
        } catch (Exception ex) {
          log.error("Error while updating user group {}", userGroup != null ? userGroup.getUuid() : "NA", ex);
        }
      }
    }
    log.info("Completed updating admin user groups with CE_ADMIN permission type");
  }
}
