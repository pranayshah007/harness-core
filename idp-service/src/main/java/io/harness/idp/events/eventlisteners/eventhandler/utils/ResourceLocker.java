/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.eventhandler.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
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

  public AcquiredLock acquireLock(String entityType, String messageId, String accountIdentifier,
      String resourceIdentifier) throws InterruptedException {
    AcquiredLock lock;
    while (true) {
      lock = redisLocker.tryToAcquireLock(
          accountIdentifier + "_" + entityType + "_" + resourceIdentifier, Duration.ofMinutes(1));

      if (lock == null) {
        log.info(
            "Lock not acquired for crud event for resource of type {} with identifier {} for account {} with message id - {}, waiting to acquire lock",
            entityType, resourceIdentifier, accountIdentifier, messageId);
        Thread.sleep(1000);
      } else {
        log.info(
            "Lock acquired for processing crud event for resource of type {} with identifier {} for account {} with message id - {}",
            entityType, resourceIdentifier, accountIdentifier, messageId);
        break;
      }
    }
    return lock;
  }

  public void releaseLock(AcquiredLock lock, String entityType, String messageId, String accountIdentifier,
      String resourceIdentifier) throws InterruptedException {
    redisLocker.destroy(lock);
    Thread.sleep(200);
    log.debug(
        "Lock released for processing crud event for resource of type {} with identifier {} for account {} with message id - {}",
        entityType, resourceIdentifier, accountIdentifier, messageId);
  }
}
