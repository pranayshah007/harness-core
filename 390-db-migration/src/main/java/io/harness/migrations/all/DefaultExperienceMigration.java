/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.migrations.Migration;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultExperienceMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("Starting the migration of default experience in account");
    Query<Account> accountsQuery =
        wingsPersistence.createQuery(Account.class).field(AccountKeys.defaultExperience).doesNotExist();
    try (HIterator<Account> records = new HIterator<>(accountsQuery.fetch())) {
      while (records.hasNext()) {
        Account account = null;
        try {
          account = records.next();
          UpdateOperations<Account> updateOperations = wingsPersistence.createUpdateOperations(Account.class);
          if (account.isCreatedFromNG()) {
            updateOperations.set(AccountKeys.defaultExperience, DefaultExperience.NG);
          } else {
            updateOperations.set(AccountKeys.defaultExperience, DefaultExperience.CG);
          }
          wingsPersistence.update(account, updateOperations);
        } catch (Exception e) {
          log.error(
              "Error while updating default experience for account: {}", account != null ? account.getUuid() : "", e);
        }
      }
    }
    log.info("Migration of default experience in account finished");
  }
}
