/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;
import io.harness.scheduler.PersistentScheduler;

import software.wings.beans.Account;
import software.wings.dl.WingsPersistence;
import software.wings.scheduler.InstanceStatsDeleteJob;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddInstanceStatsDeletionJobToAllAccounts implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject @Named("BackgroundJobScheduler") private transient PersistentScheduler jobScheduler;

  @Override
  public void migrate() {
    Query<Account> query = wingsPersistence.createQuery(Account.class);

    try (HIterator<Account> records = new HIterator<>(query.fetch())) {
      for (Account account : records) {
        InstanceStatsDeleteJob.delete(jobScheduler, account.getUuid());
        InstanceStatsDeleteJob.add(jobScheduler, account.getUuid());
        log.info("Added InstanceStatsDeleteJob for account {}", account.getUuid());
      }
    }
  }
}
