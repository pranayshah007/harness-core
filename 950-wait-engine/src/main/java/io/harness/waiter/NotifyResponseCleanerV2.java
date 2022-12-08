/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.waiter;

import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.PersistentLockException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class NotifyResponseCleanerV2 implements Runnable {
  @Inject private QueueController queueController;
  @Inject private NotifyResponseCleanupHelper notifyResponseCleanupHelper;
  @Inject private PersistentLocker persistentLocker;
  private static final String NOTIFY_RESPONSE_LOCK = "NOTIFY_RESPONSE_LOCK";

  @Override
  public void run() {
    if (getMaintenanceFlag() || queueController.isNotPrimary()) {
      return;
    }

    try {
      try (AcquiredLock lock = persistentLocker.waitToAcquireLock(
               NotifyResponse.class, NOTIFY_RESPONSE_LOCK, Duration.ofSeconds(10), Duration.ofSeconds(1))) {
        notifyResponseCleanupHelper.execute();
      }
    } catch (PersistentLockException ex) {
      log.info("Unable to acquire lock");
    } catch (Exception e) {
      log.error("Exception happened in Notifier execute", e);
    }
  }
}
