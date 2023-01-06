/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.ServiceVariableService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rsingh on 6/1/18.
 */
@Slf4j
public class SecretTextFilterMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ServiceVariableService serviceVariableService;

  @Override
  public void migrate() {
    Query<Account> query = wingsPersistence.createQuery(Account.class);

    try (HIterator<Account> records = new HIterator<>(query.fetch())) {
      for (Account account : records) {
        int updatedRecords = serviceVariableService.updateSearchTagsForSecrets(account.getUuid());
        log.info("updated {} for account {}", updatedRecords, account.getUuid());
      }
    }
  }
}
