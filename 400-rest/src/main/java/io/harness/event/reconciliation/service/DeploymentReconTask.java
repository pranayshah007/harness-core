/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.reconciliation.service;

import io.harness.beans.FeatureName;
import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.timeseries.processor.DeploymentEventProcessor;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;

import software.wings.beans.Account;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeploymentReconTask implements Runnable {
  @Inject DeploymentReconService deploymentReconService;
  @Inject AccountService accountService;
  @Inject DeploymentEventProcessor deploymentEventProcessor;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private PersistentLocker persistentLocker;

  private static final Integer DATA_MIGRATION_INTERVAL_IN_HOURS = 24;
  // On safe side, cron cycle is around 15 minutes, so lock expiry set to 16 min
  // Allowing 2 cycles to complete the migration task in case it takes longer
  private static final long DATA_MIGRATION_CRON_LOCK_EXPIRY_IN_SECONDS = 960; // 60 * 16
  private static final String DATA_MIGRATION_CRON_LOCK_PREFIX = "DEPLOYMENT_DATA_MIGRATION_CRON:";

  /**
   * Fixed size threadPool to have max 5 threads only
   */
  @Inject @Named("DeploymentReconTaskExecutor") ExecutorService executorService;
  @Override
  public void run() {
    try {
      long startTime = System.currentTimeMillis();
      List<Account> accountList = accountService.listAllAccountWithDefaultsWithoutLicenseInfo();
      for (Account account : accountList) {
        executorService.submit(() -> {
          final long durationStartTs = startTime - 45 * 60 * 1000;
          final long durationEndTs = startTime - 5 * 60 * 1000;
          try {
            ReconciliationStatus reconciliationStatus =
                deploymentReconService.performReconciliation(account.getUuid(), durationStartTs, durationEndTs);
            log.info(
                "Completed reconciliation for accountID:[{}],accountName:[{}] durationStart:[{}],durationEnd:[{}],status:[{}]",
                account.getUuid(), account.getAccountName(), new Date(durationStartTs), new Date(durationEndTs),
                reconciliationStatus);
          } catch (Exception e) {
            log.error(
                "Error while performing reconciliation for accountID:[{}],accountName:[{}] durationStart:[{}],durationEnd:[{}]",
                account.getUuid(), account.getAccountName(), new Date(durationStartTs), new Date(durationEndTs), e);
          }

          try (AcquiredLock lock =
                   persistentLocker.tryToAcquireLock(Account.class, DATA_MIGRATION_CRON_LOCK_PREFIX + account.getUuid(),
                       Duration.ofSeconds(DATA_MIGRATION_CRON_LOCK_EXPIRY_IN_SECONDS))) {
            if (lock == null) {
              log.error(
                  "Unable to fetch lock for running deployment data migration for account : {}", account.getUuid());
              return;
            }

            if (featureFlagService.isEnabled(
                    FeatureName.CUSTOM_DASHBOARD_ENABLE_CRON_DEPLOYMENT_DATA_MIGRATION, account.getUuid())) {
              log.info("Triggering deployment data migration cron for account : {}", account.getUuid());
              try {
                deploymentEventProcessor.doDataMigration(account.getUuid(), DATA_MIGRATION_INTERVAL_IN_HOURS);
              } catch (Exception exception) {
                log.error("Deployment data migration failed for account id : {}", account.getUuid(), exception);
              }
            }
          }
        });
      }
    } catch (Exception e) {
      log.error("Failed to run reconcilation", e);
    }
  }
}
