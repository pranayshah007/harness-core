/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.seedata;

import static io.harness.exception.WingsException.ExecutionContext.MANAGER;

import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.migrations.SeedDataMigration;

import software.wings.beans.Account;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.template.TemplateGalleryService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReImportTemplatesMigration implements SeedDataMigration {
  private static final String ACCOUNT_ID = "-czOfo4UTPumhprgLZkDYg"; // Bitcentral Inc.
  @Inject private TemplateGalleryService templateGalleryService;
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    try {
      Account account = wingsPersistence.get(Account.class, ACCOUNT_ID);
      if (account == null) {
        log.info("Specified account not found. Not copying templates.");
        return;
      }
      templateGalleryService.copyHarnessTemplatesToAccountV2(account.getUuid(), account.getAccountName());
    } catch (WingsException e) {
      ExceptionLogger.logProcessedMessages(e, MANAGER, log);
      log.error("Migration failed: ", e);
    } catch (Exception e) {
      log.error("Migration failed: ", e);
    }
  }
}
