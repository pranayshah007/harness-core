/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.waiter;

import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled Task to look for finished WaitInstances and send messages to NotifyEventQueue.
 */
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class NotifyResponseCleaner implements Runnable {
  @Inject private QueueController queueController;
  @Inject private NotifyResponseCleanupHelper notifyResponseCleanupHelper;

  @Override
  public void run() {
    if (getMaintenanceFlag() || queueController.isNotPrimary()) {
      return;
    }

    try {
      notifyResponseCleanupHelper.execute();
    } catch (Exception e) {
      log.error("Exception happened in Notifier execute", e);
    }
  }
}
