/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.reconciliation.service;

import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import static java.time.Duration.ofMinutes;

import io.harness.beans.FeatureName;
import io.harness.dataretention.LongerDataRetentionService;
import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.timeseries.processor.DeploymentEventProcessor;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.AccountStatus;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.datatretention.LongerDataRetentionState;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.search.entities.deployment.DeploymentExecutionEntity;
import software.wings.search.framework.ExecutionEntity;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import dev.morphia.query.Query;
import java.time.Duration;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeploymentReconTask implements Runnable {
  @Inject AccountService accountService;
  @Inject DeploymentEventProcessor deploymentEventProcessor;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private PersistentLocker persistentLocker;
  @Inject private Set<ExecutionEntity<?>> executionEntities;
  @Inject private ConfigurationController configurationController;
  @Inject private LongerDataRetentionService longerDataRetentionService;
  private long reconDuration;

  public DeploymentReconTask(long reconDuration) {
    this.reconDuration = reconDuration;
  }

  private static final Integer DATA_MIGRATION_INTERVAL_IN_HOURS = 24;
  // On safe side, cron cycle is around 15 minutes, so lock expiry set to 16 min
  // Allowing 2 cycles to complete the migration task in case it takes longer
  private static long DATA_MIGRATION_CRON_LOCK_EXPIRY_IN_SECONDS = 960; // 60 * 16
  private static final String DATA_MIGRATION_CRON_LOCK_PREFIX = "DEPLOYMENT_DATA_MIGRATION_CRON:";

  /**
   * Fixed size threadPool to have max 5 threads only
   */
  @Inject @Named("DeploymentReconTaskExecutor") ExecutorService executorService;
  @Override
  public void run() {
    if (!shouldRun()) {
      return;
    }
    DATA_MIGRATION_CRON_LOCK_EXPIRY_IN_SECONDS = reconDuration + 60;

    try (AcquiredLock<?> jobLock = persistentLocker.tryToAcquireLock("DeploymentReconciliationJob", ofMinutes(5))) {
      if (jobLock == null) {
        log.warn("Couldn't acquire lock for DeploymentReconciliationJob");
        return;
      }
      long startTime = System.currentTimeMillis();
      Query<Account> query = accountService.getBasicAccountWithLicenseInfoQuery().limit(NO_LIMIT);
      try (HIterator<Account> iterator = new HIterator<>(query.fetch())) {
        for (Account account : iterator) {
          if (account.getLicenseInfo() == null
              || !AccountStatus.ACTIVE.equals(account.getLicenseInfo().getAccountStatus())) {
            continue;
          }
          for (ExecutionEntity executionEntity : executionEntities) {
            if (!DeploymentExecutionEntity.SOURCE_ENTITY_CLASS.equals(executionEntity.getSourceEntityClass())) {
              continue;
            }

            executorService.submit(() -> {
              final long durationStartTs = startTime - (2 * reconDuration + 900) * 1000;
              final long durationEndTs = startTime - 5 * 60 * 1000;
              try {
                ReconciliationStatus reconciliationStatus = executionEntity.getReconService().performReconciliation(
                    account.getUuid(), durationStartTs, durationEndTs, executionEntity);
                log.info(
                    "Completed reconciliation for accountID:[{}],accountName:[{}] durationStart:[{}],durationEnd:[{}],status:[{}],entity[{}]",
                    account.getUuid(), account.getAccountName(), new Date(durationStartTs), new Date(durationEndTs),
                    reconciliationStatus, executionEntity);
              } catch (Exception e) {
                log.error(
                    "Error while performing reconciliation for accountID:[{}],accountName:[{}] durationStart:[{}],durationEnd:[{}],entity[{}]",
                    account.getUuid(), account.getAccountName(), new Date(durationStartTs), new Date(durationEndTs),
                    executionEntity, e);
              }

              if (WorkflowExecution.class.equals(executionEntity.getSourceEntityClass())) {
                try (AcquiredLock lock = persistentLocker.tryToAcquireLock(Account.class,
                         DATA_MIGRATION_CRON_LOCK_PREFIX + account.getUuid(),
                         Duration.ofSeconds(DATA_MIGRATION_CRON_LOCK_EXPIRY_IN_SECONDS))) {
                  if (lock == null) {
                    log.error("Unable to fetch lock for running deployment data migration for account : {}",
                        account.getUuid());
                    return;
                  }

                  if (featureFlagService.isEnabled(
                          FeatureName.CUSTOM_DASHBOARD_ENABLE_CRON_DEPLOYMENT_DATA_MIGRATION, account.getUuid())
                      && !longerDataRetentionService.isLongerDataRetentionCompleted(
                          LongerDataRetentionState.DEPLOYMENT_LONGER_RETENTION, account.getUuid())) {
                    log.info("Triggering deployment data migration cron for account : {}", account.getUuid());
                    try {
                      deploymentEventProcessor.doDataMigration(account.getUuid(), DATA_MIGRATION_INTERVAL_IN_HOURS);
                    } catch (Exception exception) {
                      log.error("Deployment data migration failed for account id : {}", account.getUuid(), exception);
                    }
                  }
                }
              }
            });
          }
        }
      }
    } catch (Exception e) {
      log.error("Failed to run reconciliation", e);
    }
  }

  private boolean shouldRun() {
    return !getMaintenanceFlag() && configurationController.isPrimary();
  }
}
