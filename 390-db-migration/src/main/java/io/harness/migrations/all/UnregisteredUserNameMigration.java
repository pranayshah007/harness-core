/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.User;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UnregisteredUserNameMigration implements Migration {
  public static final String NOT_REGISTERED = "<Not registered yet>";

  @Inject WingsPersistence wingsPersistence;
  @Override
  public void migrate() {
    log.info("Migrating unregistered usernames");

    Query<User> query = wingsPersistence.createQuery(User.class).field("name").equal(NOT_REGISTERED);
    log.info("Updating " + query.count() + " user entries");
    try (HIterator<User> userIterator = new HIterator<>(query.fetch())) {
      for (User user : userIterator) {
        wingsPersistence.update(user, wingsPersistence.createUpdateOperations(User.class).set("name", user.getEmail()));
      }
    }
  }
}
