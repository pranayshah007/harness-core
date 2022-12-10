/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.alert.AlertNotificationRule;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AlertNotificationRuleService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class CreateDefaultAlertNotificationRule implements Migration {
  @Inject private AccountService accountService;
  @Inject private AlertNotificationRuleService alertNotificationRuleService;

  @Override
  public void migrate() {
    log.info("Creating default alert notification rules for all accounts.");

    try {
      Query<Account> query = accountService.getBasicAccountQuery();

      try (HIterator<Account> accounts = new HIterator<>(query.fetch())) {
        for (Account account : accounts) {
          String accountId = account.getUuid();
          if (GLOBAL_ACCOUNT_ID.equals(accountId)) {
            continue;
          }

          AlertNotificationRule rule = alertNotificationRuleService.createDefaultRule(accountId);
          if (null == rule) {
            log.error("No default notification rule create. accountId={}", accountId);
          }
        }
      }
    } catch (Exception e) {
      log.error("Error creating default notification rules", e);
    }
  }
}
