/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;

import io.harness.limits.Action;
import io.harness.limits.ActionType;
import io.harness.limits.Counter;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;

import com.google.inject.Inject;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class InitServiceCounters implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;
  @Inject private AppService appService;

  @Override
  public void migrate() {
    log.info("Initializing Service Counters");

    try {
      wingsPersistence.delete(
          wingsPersistence.createQuery(Counter.class).field("key").endsWith(ActionType.CREATE_SERVICE.toString()));

      Query<Account> query = accountService.getBasicAccountQuery();

      try (HIterator<Account> accounts = new HIterator<>(query.fetch())) {
        for (Account account : accounts) {
          String accountId = account.getUuid();
          if (GLOBAL_ACCOUNT_ID.equals(accountId)) {
            continue;
          }

          Set<String> appIds =
              appService.getAppsByAccountId(accountId).stream().map(Application::getUuid).collect(Collectors.toSet());

          long serviceCount = wingsPersistence.createQuery(Service.class).field("appId").in(appIds).count();

          Action action = new Action(accountId, ActionType.CREATE_SERVICE);

          log.info("Initializing Counter. Account Id: {} , ServiceCount: {}", accountId, serviceCount);
          Counter counter = new Counter(action.key(), serviceCount);
          wingsPersistence.save(counter);
        }
      }
    } catch (Exception e) {
      log.error("Error initializing Service counters", e);
    }
  }
}
