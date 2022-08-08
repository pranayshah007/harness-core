/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.entities.user;

import io.harness.persistence.PersistentEntity;

import software.wings.beans.User;
import software.wings.search.framework.ChangeHandler;
import software.wings.search.framework.TimeScaleEntity;
import software.wings.timescale.migrations.MigrateUsersToTimeScaleDB;

import com.google.inject.Inject;
import java.util.Set;

public class UserTimeScaleEntity implements TimeScaleEntity<User> {
  @Inject private UserTimescaleChangeHandler userTimescaleChangeHandler;
  @Inject private MigrateUsersToTimeScaleDB migrateUsersToTimeScaleDB;

  public static final Class<User> SOURCE_ENTITY_CLASS = User.class;

  @Override
  public Class<User> getSourceEntityClass() {
    return SOURCE_ENTITY_CLASS;
  }

  @Override
  public ChangeHandler getChangeHandler() {
    return userTimescaleChangeHandler;
  }

  @Override
  public boolean toProcessChangeEvent(Set<String> accountIds, PersistentEntity entity) {
    User user = (User) entity;

    for (String accountId : user.getAccountIds()) {
      if (accountIds.contains(accountId)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean runMigration(String accountId) {
    return migrateUsersToTimeScaleDB.runTimeScaleMigration(accountId);
  }
}
