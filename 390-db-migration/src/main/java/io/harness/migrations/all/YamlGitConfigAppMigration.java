/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.common.Constants.ACCOUNT_ID_KEY;

import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.yaml.gitSync.beans.YamlGitConfig;
import software.wings.yaml.gitSync.beans.YamlGitConfig.SyncMode;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YamlGitConfigAppMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private YamlGitService yamlGitService;

  @Override
  public void migrate() {
    log.info("Running YamlGitConfigAppMigration");

    try (HIterator<Account> accountHIterator =
             new HIterator<>(wingsPersistence.createQuery(Account.class, excludeAuthority).fetch())) {
      while (accountHIterator.hasNext()) {
        String accountId = accountHIterator.next().getUuid();

        YamlGitConfig yamlGitConfig = yamlGitService.get(accountId, accountId, EntityType.ACCOUNT);

        if (yamlGitConfig != null) {
          log.info("Retrieving applications for accountId " + accountId);
          try (HIterator<Application> apps = new HIterator<>(
                   wingsPersistence.createQuery(Application.class).filter(ACCOUNT_ID_KEY, accountId).fetch())) {
            while (apps.hasNext()) {
              Application application = apps.next();
              saveYamlGitConfigForApp(application, yamlGitConfig);
            }
          }
          log.info("Done updating applications for accountId " + accountId);
        }
      }
    }

    log.info("Completed running YamlGitConfigAppMigration");
  }

  private void saveYamlGitConfigForApp(Application app, YamlGitConfig yamlGitConfig) {
    YamlGitConfig savedYamlGitConfig = yamlGitService.get(app.getAccountId(), app.getUuid(), EntityType.APPLICATION);
    if (savedYamlGitConfig != null) {
      return;
    }

    YamlGitConfig newYamlGitConfig = YamlGitConfig.builder()
                                         .enabled(true)
                                         .syncMode(SyncMode.BOTH)
                                         .entityId(app.getUuid())
                                         .entityType(EntityType.APPLICATION)
                                         .gitConnectorId(yamlGitConfig.getGitConnectorId())
                                         .branchName(yamlGitConfig.getBranchName())
                                         .accountId(yamlGitConfig.getAccountId())
                                         .build();
    newYamlGitConfig.setAppId(app.getUuid());

    if (yamlGitConfig.getEncryptedPassword() != null) {
      EncryptedData encryptedData = wingsPersistence.createQuery(EncryptedData.class)
                                        .filter(EncryptedDataKeys.accountId, yamlGitConfig.getAccountId())
                                        .filter("_id", yamlGitConfig.getEncryptedPassword())
                                        .get();

      if (encryptedData != null) {
        String encryptedPassword = new StringBuilder()
                                       .append(encryptedData.getEncryptionType().getYamlName())
                                       .append(":")
                                       .append(yamlGitConfig.getEncryptedPassword())
                                       .toString();

        newYamlGitConfig.setEncryptedPassword(encryptedPassword);
      }
    }

    wingsPersistence.save(newYamlGitConfig);
  }
}
