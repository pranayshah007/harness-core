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
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class InstanceComputerProviderNameFixMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;
  @Inject private SettingsService settingsService;

  @Override
  public void migrate() {
    try {
      Query<Account> query = accountService.getBasicAccountQuery();

      try (HIterator<Account> accounts = new HIterator<>(query.fetch())) {
        for (Account account : accounts) {
          log.info("Updating data for account:" + account.getAccountName());
          try (HIterator<Instance> instanceRecords =
                   new HIterator<Instance>(wingsPersistence.createQuery(Instance.class)
                                               .filter(InstanceKeys.accountId, account.getUuid())
                                               .field(InstanceKeys.computeProviderName)
                                               .doesNotExist()
                                               .fetch())) {
            while (instanceRecords.hasNext()) {
              Instance instance = instanceRecords.next();
              SettingAttribute cloudProviderSetting = settingsService.get(instance.getComputeProviderId());
              if (cloudProviderSetting != null) {
                final UpdateOperations<Instance> operations =
                    wingsPersistence.createUpdateOperations(Instance.class)
                        .set(InstanceKeys.computeProviderName, cloudProviderSetting.getName());
                wingsPersistence.update(instance, operations);
              }
            }
          }
        }
      }

    } catch (Exception e) {
      log.error("Failed to fix instance data", e);
    }
  }
}
