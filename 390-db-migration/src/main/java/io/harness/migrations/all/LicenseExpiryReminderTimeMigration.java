/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static software.wings.beans.Account.AccountKeys;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LicenseExpiryReminderTimeMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("Starting migration of lastLicenseExpiryReminderSentAt field.");
    try (HIterator<Account> accounts = new HIterator<>(wingsPersistence.createAuthorizedQuery(Account.class).fetch())) {
      while (accounts.hasNext()) {
        Account account = accounts.next();
        long lastLicenseExpiryReminderSentAt = account.getLastLicenseExpiryReminderSentAt();
        if (lastLicenseExpiryReminderSentAt != 0L) {
          UpdateOperations<Account> updateOperations = wingsPersistence.createUpdateOperations(Account.class);
          updateOperations.push(AccountKeys.licenseExpiryRemindersSentAt, lastLicenseExpiryReminderSentAt);
          wingsPersistence.update(account, updateOperations);
        }
      }
      log.info("Migration of lastLicenseExpiryReminderSentAt field is finished.");
    }
  }
}
