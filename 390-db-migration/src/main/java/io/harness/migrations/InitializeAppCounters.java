/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;

import io.harness.limits.Action;
import io.harness.limits.ActionType;
import io.harness.limits.Counter;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.Query;

/**
 * Populate `limitCounters` collection with current value of applications an account has.
 */
@Slf4j
public class InitializeAppCounters implements Migration {
  @Inject AccountService accountService;
  @Inject WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("Initializing Counters");
    Datastore ds = wingsPersistence.getDatastore(Counter.class);

    try {
      Query<Account> query = accountService.getBasicAccountQuery();
      ds.delete(ds.createQuery(Counter.class));
      try (HIterator<Account> accounts = new HIterator<>(query.fetch())) {
        for (Account account : accounts) {
          String accountId = account.getUuid();
          if (accountId.equals(GLOBAL_ACCOUNT_ID)) {
            continue;
          }

          Action action = new Action(accountId, ActionType.CREATE_APPLICATION);
          long appCount =
              ds.getCount(wingsPersistence.createQuery(Application.class).field("accountId").equal(accountId));

          log.info("Initializing Counter. Account Id: {} , AppCount: {}", accountId, appCount);
          Counter counter = new Counter(action.key(), appCount);
          wingsPersistence.save(counter);
        }
      }
    } catch (Exception e) {
      log.error("Error initializing app counters", e);
    }
  }
}
