/*
 * Copyright 2022 Harness Inc. All rights reserved.
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
public class AuditCleanupJob implements Managed {
  private static final long DELAY_IN_MINUTES = TimeUnit.HOURS.toMinutes(24);

  private static int retentionTimeInMonths = 18;
  private static final String LOCK_NAME = "AUDIT_CLEANUP_JOB_LOCK";
  @Inject private AuditService auditService;
  @Inject private PersistentLocker persistentLocker;

  private ScheduledExecutorService executorService;

  @Override
  public void start() throws Exception {
    Random random = new Random();
    executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("audit-cleanup-job").build());
    executorService.scheduleWithFixedDelay(this::run, 5 + random.nextInt(120), DELAY_IN_MINUTES, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    log.warn("Audit Cleanup is stopped");
    executorService.shutdownNow();
    executorService.awaitTermination(30, TimeUnit.SECONDS);
  }

  @VisibleForTesting
  public void run() {
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, Duration.ofSeconds(10))) {
      if (lock == null) {
        log.info(LOCK_NAME + "failed to acquire lock");
        return;
      }
      log.info("Audit Cleanup Job Started @ {}", Instant.now());
      long toBeDeletedTillTimestamp =
          LocalDateTime.now().minusMonths(retentionTimeInMonths).toInstant(ZoneOffset.UTC).toEpochMilli();
      auditService.deleteAuditRecords(toBeDeletedTillTimestamp);
    }
  }
}
