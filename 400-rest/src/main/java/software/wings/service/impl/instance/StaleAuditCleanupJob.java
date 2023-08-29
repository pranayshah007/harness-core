/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;

import software.wings.service.intfc.AuditService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.lifecycle.Managed;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class StaleAuditCleanupJob implements Managed {
  private final String DEBUG_MESSAGE = "StaleAuditCleanupJob: ";
  private static final long DELAY_IN_MINUTES = TimeUnit.MINUTES.toMinutes(1);

  private static int RETENTION_TIME_IN_MONTHS = 1;
  private static final String LOCK_NAME = "STALE_AUDIT_CLEANUP_JOB_LOCK";
  @Inject private AuditService auditService;
  @Inject private PersistentLocker persistentLocker;

  private ScheduledExecutorService executorService;

  @Override
  public void start() throws Exception {
    Random random = new Random();
    executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("stale-audit-cleanup-job").build());
    executorService.scheduleWithFixedDelay(this::run, random.nextInt(15) + 15, DELAY_IN_MINUTES, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    log.warn("Stale Audit Cleanup is stopped");
    executorService.shutdownNow();
    executorService.awaitTermination(30, TimeUnit.SECONDS);
  }

  @VisibleForTesting
  public void run() {
    log.info(DEBUG_MESSAGE + "started...");
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, Duration.ofSeconds(10))) {
      if (lock == null) {
        log.info(LOCK_NAME + "failed to acquire lock");
        return;
      }
      log.info("Stale Audit Cleanup Job Started @ {}", Instant.now());
      long toBeDeletedTillTimestamp =
          LocalDateTime.now().minusMonths(RETENTION_TIME_IN_MONTHS).toInstant(ZoneOffset.UTC).toEpochMilli();
      auditService.deleteStaleAuditRecords(toBeDeletedTillTimestamp);
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " failed to acquire lock", ex);
    }
    log.info(DEBUG_MESSAGE + " completed...");
  }
}
