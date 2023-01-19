/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.mongo.MongoUtils.setUnset;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddDisabledFieldMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    try (HIterator<User> userHIterator = new HIterator<>(
             wingsPersistence.createQuery(User.class).field(UserKeys.disabled).doesNotExist().fetch())) {
      while (userHIterator.hasNext()) {
        User user = userHIterator.next();
        if (!user.getDisabled()) {
          UpdateOperations<User> operations = wingsPersistence.createUpdateOperations(User.class);
          log.info("Setting users disabled state of user: {}, to: {}", user.getUuid(), false);
          setUnset(operations, UserKeys.disabled, false);
          wingsPersistence.update(user, operations);
        }
      }
    } catch (Exception e) {
      log.error("Could not run migration for users disabled field ", e);
    }
  }
}
