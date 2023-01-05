/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.limits.Counter;
import io.harness.limits.Counter.CounterKeys;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

/**
 * @author marklu on 2019-07-10
 */
@Slf4j
public class LimitCounterAccountIdMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("Migrating existing limit counters to add accountId field");

    int count = 0;
    try (HIterator<Counter> counters =
             new HIterator<>(wingsPersistence.createQuery(Counter.class, excludeAuthority).fetch())) {
      while (counters.hasNext()) {
        Counter counter = counters.next();
        if (counter.getAccountId() == null) {
          log.info("Updating counter {}", counter.getKey());

          counter.populateAccountIdFromKey();
          String accountId = counter.getAccountId();
          UpdateOperations<Counter> updateOperations = wingsPersistence.createUpdateOperations(Counter.class);
          updateOperations.set(CounterKeys.accountId, accountId);

          Query<Counter> query =
              wingsPersistence.createQuery(Counter.class).field(CounterKeys.key).equal(counter.getKey());
          wingsPersistence.findAndModify(query, updateOperations, WingsPersistence.upsertReturnNewOptions);
          count++;

          log.info("Completed updating counter {} with accountId {}", counter.getKey(), accountId);
        }
      }
    }

    log.info("Updated all {} counters", count);
  }
}
