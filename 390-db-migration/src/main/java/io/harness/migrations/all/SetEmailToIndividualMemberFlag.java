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
import software.wings.beans.notification.NotificationSettings;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

/**
 * Refer to https://harness.atlassian.net/browse/PL-1296 for context.
 */
@Slf4j
public class SetEmailToIndividualMemberFlag implements Migration {
  @Inject private UserGroupService userGroupService;
  @Inject private AccountService accountService;
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    try {
      log.info("Running Migration: {}", SetEmailToIndividualMemberFlag.class.getSimpleName());

      Query<Account> query = accountService.getBasicAccountQuery();

      try (HIterator<Account> accounts = new HIterator<>(query.fetch())) {
        for (Account account : accounts) {
          String accountId = account.getUuid();
          if (Account.GLOBAL_ACCOUNT_ID.equals(accountId)) {
            continue;
          }

          UserGroup userGroup = userGroupService.getDefaultUserGroup(accountId);
          if (null == userGroup) {
            log.info("No default user group present. accountId={}", accountId);
            continue;
          }

          log.info(
              "Setting useIndividualEmails flag to true. accountId={} userGroupId={}", accountId, userGroup.getUuid());
          NotificationSettings existing = userGroup.getNotificationSettings();
          NotificationSettings updatedSetting;

          if (null == existing) {
            updatedSetting = new NotificationSettings(true, true, Collections.emptyList(), null, "", "");
          } else {
            updatedSetting = new NotificationSettings(true, true, existing.getEmailAddresses(),
                existing.getSlackConfig(), "", existing.getMicrosoftTeamsWebhookUrl());
          }

          wingsPersistence.updateField(
              UserGroup.class, userGroup.getUuid(), UserGroup.NOTIFICATION_SETTINGS_KEY, updatedSetting);
        }
      }
    } catch (Exception e) {
      log.error("Error running SetEmailToIndividualMemberFlag migration", e);
    }
  }
}
