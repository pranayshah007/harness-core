/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.dl.WingsPersistence;
import software.wings.search.framework.SearchDistributedLock.SearchDistributedLockKeys;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * A lock implementation based on
 * Mongo TTL indexes.
 *
 * @author utkarsh
 */
@OwnedBy(PL)
@Slf4j
public class PerpetualSearchLocker {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ConfigurationController configurationController;
  private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder().setNameFormat("search-heartbeat-%d").build());

  private boolean isLockAcquired(String lockName, String uuid) {
    Query<SearchDistributedLock> query = wingsPersistence.createQuery(SearchDistributedLock.class)
                                             .field(SearchDistributedLockKeys.name)
                                             .equal(lockName)
                                             .field(SearchDistributedLockKeys.uuid)
                                             .equal(uuid);
    return query.get() != null && configurationController.isPrimary();
  }

  private boolean tryToAcquireLock(String lockName, String uuid) {
    if (wingsPersistence.get(SearchDistributedLock.class, lockName) == null && configurationController.isPrimary()) {
      Instant instant = Instant.now();
      SearchDistributedLock searchDistributedLock =
          new SearchDistributedLock(lockName, uuid, Date.from(instant), instant.toEpochMilli());
      wingsPersistence.save(searchDistributedLock);
      return isLockAcquired(lockName, uuid);
    }
    return false;
  }

  public ScheduledFuture acquireLock(String lockName, String uuid, LockTimeoutCallback lockTimeoutCallback)
      throws InterruptedException {
    int retryIntervalinMS = 1000;
    int readinessWaitTimeinMS = 5000;
    if (!isLockAcquired(lockName, uuid)) {
      log.info("Attempting to acquire lock");
      boolean isLockAcquired = false;
      while (!isLockAcquired) {
        Thread.sleep(retryIntervalinMS);
        isLockAcquired = tryToAcquireLock(lockName, uuid);
        if (isLockAcquired) {
          Thread.sleep(readinessWaitTimeinMS);
          isLockAcquired = isLockAcquired(lockName, uuid);
        }
      }
    }
    log.info("Search lock acquired");
    SearchHeartbeatMonitor searchHeartbeatMonitor =
        new SearchHeartbeatMonitor(wingsPersistence, lockTimeoutCallback, lockName, uuid, configurationController);
    return scheduledExecutorService.scheduleAtFixedRate(searchHeartbeatMonitor, 0, 10, TimeUnit.SECONDS);
  }

  public void shutdown() {
    if (scheduledExecutorService != null) {
      scheduledExecutorService.shutdownNow();
    }
  }

  @FunctionalInterface
  public interface LockTimeoutCallback {
    void stop();
  }
}
