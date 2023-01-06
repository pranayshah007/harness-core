/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Migration script for updating default account ids for users which are not part of that account anymore
 */
@OwnedBy(PL)
@Slf4j
public class UpdateStaleDefaultAccountIds implements Migration {
  @Inject private WingsPersistence persistence;
  @Inject private UserService userService;

  @Override
  public void migrate() {
    try (HIterator<User> usersIterator = new HIterator<>(persistence.createQuery(User.class, excludeAuthority)
                                                             .project(UserKeys.email, true)
                                                             .project(UserKeys.accounts, true)
                                                             .project(UserKeys.defaultAccountId, true)
                                                             .fetch())) {
      while (usersIterator.hasNext()) {
        final User user = usersIterator.next();

        if (user.getDefaultAccountId() == null) {
          continue;
        }

        if (isNotEmpty(user.getAccounts())) {
          Set<String> accountIds = user.getAccounts().stream().map(Account::getUuid).collect(Collectors.toSet());

          if (!accountIds.contains(user.getDefaultAccountId())) {
            log.info("User {} has stale default account id: {}", user.getEmail(), user.getDefaultAccountId());

            user.setDefaultAccountId(user.getDefaultAccountCandidate());

            log.info("New default account for user {} set to: {}", user.getEmail(), user.getDefaultAccountId());

            UpdateOperations<User> updateOp = persistence.createUpdateOperations(User.class)
                                                  .set(UserKeys.defaultAccountId, user.getDefaultAccountId());

            Query<User> updateQuery = persistence.createQuery(User.class).filter(UserKeys.email, user.getEmail());
            persistence.update(updateQuery, updateOp);
          }
        }
      }
    }
  }
}
