/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.waiter;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;
import io.harness.queue.QueueController;
import io.harness.waiter.persistence.PersistenceWrapper;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled Task to look for finished WaitInstances and send messages to NotifyEventQueue.
 */
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class NotifyResponseCleaner implements Runnable {
  @Inject private HPersistence persistence;
  @Inject private PersistenceWrapper persistenceWrapper;
  @Inject private QueueController queueController;
  @Inject private WaitNotifyEngine waitNotifyEngine;

  @Override
  public void run() {
    if (getMaintenanceFlag() || queueController.isNotPrimary()) {
      return;
    }

    try {
      execute();
    } catch (Exception e) {
      log.error("Exception happened in Notifier execute", e);
    }
  }

  public void execute() {
    try {
      executeInternal();
    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception exception) {
      log.error("Error seen in the Notifier call", exception);
    }
  }

  public void executeInternal() {
    log.debug("Execute Notifier response processing");

    // Sometimes response might arrive before we schedule the wait. Do not remove responses that are very new.
    final long limit = System.currentTimeMillis() - Duration.ofSeconds(15).toMillis();

    List<String> deleteResponses = new ArrayList<>();
    List<String> keys = persistenceWrapper.fetchNotifyResponseKeys(limit);
    boolean needHandling = false;
    for (String key : keys) {
      List<WaitInstance> waitInstances = persistenceWrapper.fetchWaitInstances(key);

      if (isEmpty(waitInstances)) {
        deleteResponses.add(key);
        if (deleteResponses.size() >= 1000) {
          deleteObsoleteResponses(deleteResponses);
          deleteResponses.clear();
        }
      }

      for (WaitInstance waitInstance : waitInstances) {
        if (isEmpty(waitInstance.getWaitingOnCorrelationIds())) {
          if (waitInstance.getCallbackProcessingAt() < System.currentTimeMillis()) {
            waitNotifyEngine.sendNotification(waitInstance);
          }
        } else if (waitInstance.getWaitingOnCorrelationIds().contains(key)) {
          needHandling = true;
        }
      }

      if (needHandling) {
        waitNotifyEngine.handleNotifyResponse(key);
      }
    }

    deleteObsoleteResponses(deleteResponses);
  }

  private void deleteObsoleteResponses(List<String> deleteResponses) {
    persistenceWrapper.deleteNotifyResponses(deleteResponses);
  }
}
