/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.eventhandler.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.redis.RedisPersistentLocker;

import com.google.inject.Inject;
import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class ResourceLocker {
  RedisPersistentLocker redisLocker;

  public AcquiredLock acquireLock(String lockName) throws InterruptedException {
    return acquireLock(lockName, 1);
  }

  public AcquiredLock acquireLock(String lockName, long minutes) throws InterruptedException {
    AcquiredLock lock = redisLocker.tryToAcquireLock(lockName, Duration.ofMinutes(minutes));
    if (lock == null) {
      log.warn("Lock not acquired for {}, will attempt in next delivery", lockName);
      Thread.sleep(1000);
      throw new UnexpectedException("Unable to acquire lock " + lockName + " , will attempt in next delivery");
    } else {
      log.info("Lock acquired for {}", lockName);
    }
    return lock;
  }

  public void releaseLock(AcquiredLock lock) throws InterruptedException {
    redisLocker.destroy(lock);
    Thread.sleep(200);
    log.debug("Lock released for {}", lock.getLock());
  }
}
