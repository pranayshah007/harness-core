/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static software.wings.beans.Account.AccountKeys;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddIsCrossGenerationAccessEnabledToAccountMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  private static final List<String> PROD_ACCOUNT_ID_LIST =
      Arrays.asList("WvvS1AaWR5Kuqeeb2jqGrw", "SNM_3IzhRa6SFPz6DIV7aA", "vpCkHKsDSxK9_KYfjCTMKA");

  @Override
  public void migrate() {
    log.info("Starting the migration to add new isCrossGenerationAccessEnabled field into all the accounts");

    Query<Account> accountsQuery =
        wingsPersistence.createQuery(Account.class)
            .field(AccountKeys.uuid)
            .hasAnyOf(
                PROD_ACCOUNT_ID_LIST) // Adding this filter to restrict this migration to only 3 accounts in PROD env.
            .field(AccountKeys.isCrossGenerationAccessEnabled)
            .doesNotExist();

    try (HIterator<Account> records = new HIterator<>(accountsQuery.fetch())) {
      while (records.hasNext()) {
        Account account = null;
        try {
          account = records.next();
          UpdateOperations<Account> updateOperations = wingsPersistence.createUpdateOperations(Account.class);
          updateOperations.set(AccountKeys.isCrossGenerationAccessEnabled, Boolean.TRUE);

          wingsPersistence.update(account, updateOperations);
          log.info("isCrossGenerationAccessEnabled field is successfully added into account: {}", account.getUuid());
        } catch (Exception e) {
          log.error("Error while updating isCrossGenerationAccessEnabled field for account: {}",
              account != null ? account.getUuid() : "", e);
        }
      }
    }
    log.info("Migration to add new isCrossGenerationAccessEnabled field into all the accounts finished");
  }
}
