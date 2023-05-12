/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;

/**
 * Previously user invites are not deleted if the corresponding user with the same email address has been deleted.
 * This migration is to clean up all user invites that their corresponding users no longer exist in the user collection.
 * @author marklu on 2018-12-09
 */
@Slf4j
public class DanglingUserInviteCleanupMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private UserService userService;

  @Override
  public void migrate() {
    Query<UserInvite> userInviteQuery =
        wingsPersistence.createQuery(UserInvite.class, excludeAuthority).limit(NO_LIMIT);
    try (HIterator<UserInvite> records = new HIterator<>(userInviteQuery.fetch())) {
      while (records.hasNext()) {
        UserInvite userInvite = records.next();
        String email = userInvite.getEmail();
        User user = userService.getUserByEmail(email);
        if (user == null) {
          // User has been deleted already. Remove corresponding user invites!
          wingsPersistence.delete(userInvite);
          log.info("User '{}' has been deleted. Deleting it's corresponding user invite.", email);
        }
      }
    }
  }
}
