/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.MANAGER;
import static io.harness.beans.FeatureName.PL_UPDATE_CONNECTOR_HEARTBEAT_PPT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.LogLevel.ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.logging.LogLevel;
import io.harness.perpetualtask.PerpetualTaskState;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.perpetualtask.internal.PerpetualTaskRecord.PerpetualTaskRecordKeys;
import io.harness.persistence.HPersistence;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import software.wings.service.intfc.AccountService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import com.mongodb.DBCollection;
import io.dropwizard.lifecycle.Managed;
import java.time.Duration;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(PL)
public class UpdateHeartBeatIntervalAndResetPerpetualTaskJob implements Managed {
  private static final long DELAY_IN_MINUTES = 120;
  private static final long NEW_INTERVAL = 1800;

  private static final String LOCK_NAME = "PERPETUAL_TASK_LEVEL_DATA_LOCK";
  private final String DEBUG_MESSAGE = "PerpetualTaskUpdation: ";

  @Inject private PersistentLocker persistentLocker;
  @Inject AccountService accountService;
  @Inject private HPersistence persistence;

  private ScheduledExecutorService executorService;

  @Override
  public void start() throws Exception {
    Random random = new Random();
    executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("heart-beat-perpetual-task-reset-job").build());
    executorService.scheduleWithFixedDelay(this::run, 15 + random.nextInt(15), DELAY_IN_MINUTES, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    log.warn(DEBUG_MESSAGE + " is stopped");
    executorService.shutdownNow();
    executorService.awaitTermination(30, TimeUnit.SECONDS);
  }

  @VisibleForTesting
  public void run() {
    log.info(DEBUG_MESSAGE + "started...");
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(DEBUG_MESSAGE + "failed to acquire lock");
        return;
      }
      try {
        SecurityContextBuilder.setContext(new ServicePrincipal(MANAGER.getServiceId()));
        log.info(DEBUG_MESSAGE + "Setting SecurityContext completed and migration started");
        execute();
        log.info(DEBUG_MESSAGE + "Migration completed");
      } catch (Exception ex) {
        log.error(DEBUG_MESSAGE + " unexpected error occurred while Setting SecurityContext", ex);
      } finally {
        SecurityContextBuilder.unsetCompleteContext();
        log.info(DEBUG_MESSAGE + "Unsetting SecurityContext completed.");
      }
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " failed to acquire lock", ex);
    }
    log.info(DEBUG_MESSAGE + " completed...");
  }

  public void execute() {
    Set<String> ffEnabledAccountList = new HashSet<>();
    try {
      ffEnabledAccountList = accountService.getFeatureFlagEnabledAccountIds(PL_UPDATE_CONNECTOR_HEARTBEAT_PPT.name());
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + "Failed to fetch all accounts with FF PL_UPDATE_CONNECTOR_HEARTBEAT_PPT", ex);
    }
    if (isEmpty(ffEnabledAccountList)) {
      log.info(DEBUG_MESSAGE + "Migration skipped for this iteration because no FF enabled account");
      return;
    }

    for (String ffEnabledAccount : ffEnabledAccountList) {
      try {
        log.info(DEBUG_MESSAGE + "Migration starts for account {}", ffEnabledAccount);
        updateForAccount(ffEnabledAccount);
      } catch (Exception ex) {
        log.error(DEBUG_MESSAGE + "Failed to migrate users for account {} ", ffEnabledAccount, ex);
      }
    }
  }

  public void updateForAccount(String accountId) {
    try {
      final DBCollection collection = persistence.getCollection(PerpetualTaskRecord.class);
      BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();
      BasicDBObject updateOperations = new BasicDBObject(PerpetualTaskRecordKeys.intervalSeconds, NEW_INTERVAL)
                                           .append(PerpetualTaskRecordKeys.state, PerpetualTaskState.TASK_UNASSIGNED)
                                           .append(PerpetualTaskRecordKeys.delegateId, "");

      bulkWriteOperation
          .find(persistence.createQuery(PerpetualTaskRecord.class)
                    .filter(PerpetualTaskRecordKeys.accountId, accountId)
                    .getQueryObject())
          .update(new BasicDBObject("$set", updateOperations)
                      .append("$unset",
                          ImmutableMap.of(
                              PerpetualTaskRecordKeys.assignTryCount, PerpetualTaskRecordKeys.unassignedReason)));
      BulkWriteResult result = bulkWriteOperation.execute();
      log.info(DEBUG_MESSAGE + "Total of {} docs are updated for account {}", result.getMatchedCount(), accountId);
    } catch (Exception e) {
      decorateWithDebugStringAndLog("message", ERROR, e);
    }
  }

  private void decorateWithDebugStringAndLog(String logLine, LogLevel logLevel, Exception ex) {
    String logFormat = "%s %s";
    switch (logLevel) {
      case INFO:
        log.info(String.format(logFormat, DEBUG_MESSAGE, logLine));
        break;
      case WARN:
        log.warn(String.format(logFormat, DEBUG_MESSAGE, logLine));
        break;
      case ERROR:
        log.error(String.format(logFormat, DEBUG_MESSAGE, logLine), ex);
        break;
      default:
    }
  }
}
