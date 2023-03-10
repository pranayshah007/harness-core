/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static software.wings.beans.Account.AccountKeys;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.migrations.Migration;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PLG)
public class ForAllAccountsAddIsCrossGenerationAccessEnabledMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;

  @Override
  public void migrate() {
    log.info("Starting the migration to add new isCrossGenerationAccessEnabled field into all the accounts");

    Query<Account> accountsQuery =
        wingsPersistence.createQuery(Account.class).field(AccountKeys.isCrossGenerationAccessEnabled).doesNotExist();
    log.info("Migration will run for total {} accounts", accountsQuery.count());
    Set<String> accountsLeftForMigration = new HashSet<>();

    try (HIterator<Account> records = new HIterator<>(accountsQuery.fetch())) {
      while (records.hasNext()) {
        Account account = null;
        try {
          account = records.next();
          accountsLeftForMigration.add(account.getUuid());
          UpdateOperations<Account> updateOperations = wingsPersistence.createUpdateOperations(Account.class);

          if (DefaultExperience.isNGExperience(account.getDefaultExperience())) {
            updateOperations.set(AccountKeys.isCrossGenerationAccessEnabled, Boolean.TRUE);
          } else {
            boolean isFFEnabled =
                accountService.isFeatureFlagEnabled(FeatureName.PL_HIDE_LAUNCH_NEXTGEN.name(), account.getUuid());

            if (isFFEnabled) {
              updateOperations.set(AccountKeys.isCrossGenerationAccessEnabled, Boolean.FALSE);
            } else {
              updateOperations.set(AccountKeys.isCrossGenerationAccessEnabled, Boolean.TRUE);
            }
          }

          wingsPersistence.update(account, updateOperations);
          accountsLeftForMigration.remove(account.getUuid());
          log.info("isCrossGenerationAccessEnabled field is successfully added into account: {}", account.getUuid());
        } catch (Exception e) {
          log.error("Error while updating isCrossGenerationAccessEnabled field for account: {}",
              account != null ? account.getUuid() : "", e);
        }
      }
    } finally {
      log.info(
          "Total accounts with failed migration= {} and the list= {}", accountsQuery.count(), accountsLeftForMigration);
    }

    log.info("Migration to add new isCrossGenerationAccessEnabled field into all the accounts finished");
  }
}
