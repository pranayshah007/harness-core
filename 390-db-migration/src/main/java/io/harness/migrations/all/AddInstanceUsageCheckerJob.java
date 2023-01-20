/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;
import io.harness.scheduler.PersistentScheduler;

import software.wings.beans.Account;
import software.wings.dl.WingsPersistence;
import software.wings.scheduler.ServiceInstanceUsageCheckerJob;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;

/**
 * Adds ServiceInstanceUsageCheckerJob for all  accounts
 */
@Slf4j
public class AddInstanceUsageCheckerJob implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject @Named("BackgroundJobScheduler") private transient PersistentScheduler jobScheduler;

  @Override
  public void migrate() {
    Query<Account> query = wingsPersistence.createQuery(Account.class);

    try (HIterator<Account> records = new HIterator<>(query.fetch())) {
      while (records.hasNext()) {
        Account account = records.next();
        if (Account.GLOBAL_ACCOUNT_ID.equals(account.getUuid())) {
          continue;
        }

        ServiceInstanceUsageCheckerJob.delete(jobScheduler, account.getUuid());
        ServiceInstanceUsageCheckerJob.addWithDelay(jobScheduler, account.getUuid());
        log.info("Added ServiceInstanceUsageCheckerJob for account {}", account.getUuid());
      }
    }
  }
}
