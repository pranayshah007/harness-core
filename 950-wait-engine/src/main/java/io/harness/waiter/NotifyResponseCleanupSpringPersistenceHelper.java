/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.waiter;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.waiter.persistence.SpringPersistenceWrapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.CloseableIterator;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class NotifyResponseCleanupSpringPersistenceHelper {
  private static final int MAX_BATCH_SIZE = 500;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private SpringPersistenceWrapper persistenceWrapper;

  public void execute() {
    try {
      executeInternal();
    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception exception) {
      log.error("Error seen in the Notifier call", exception);
    }
  }

  private void executeInternal() {
    log.debug("Execute Notifier response processing");

    // Sometimes response might arrive before we schedule the wait. Do not remove responses that are very new.
    final long limit = System.currentTimeMillis() - Duration.ofSeconds(15).toMillis();

    Set<String> keys = new HashSet<>();
    try (CloseableIterator<NotifyResponse> notifyResponseIterator =
             persistenceWrapper.fetchNotifyResponseKeysFromSecondary(limit)) {
      while (notifyResponseIterator.hasNext()) {
        keys.add(notifyResponseIterator.next().getUuid());

        if (keys.size() >= MAX_BATCH_SIZE) {
          sendAndHandleNotifyResponses(keys);
          keys.clear();
        }
      }
    }
    sendAndHandleNotifyResponses(keys);
  }

  private void sendAndHandleNotifyResponses(Set<String> keys) {
    List<String> deleteResponses = new ArrayList<>();
    for (String key : keys) {
      try (NotifyResponseLogContext ignore = new NotifyResponseLogContext(key, OVERRIDE_ERROR)) {
        boolean needHandling = false;
        try (CloseableIterator<WaitInstance> iterator = persistenceWrapper.fetchWaitInstancesFromSecondary(key)) {
          if (!iterator.hasNext()) {
            deleteResponses.add(key);
          } else {
            while (iterator.hasNext()) {
              WaitInstance waitInstance = iterator.next();
              if (isEmpty(waitInstance.getWaitingOnCorrelationIds())) {
                if (waitInstance.getCallbackProcessingAt() < System.currentTimeMillis()) {
                  waitNotifyEngine.sendNotification(waitInstance);
                }
              } else if (waitInstance.getWaitingOnCorrelationIds().contains(key)) {
                needHandling = true;
              }
            }
          }
        }

        if (needHandling) {
          waitNotifyEngine.handleNotifyResponse(key);
        }
      }
    }
    deleteObsoleteResponses(deleteResponses);
  }

  private void deleteObsoleteResponses(List<String> deleteResponses) {
    if (EmptyPredicate.isNotEmpty(deleteResponses)) {
      persistenceWrapper.deleteNotifyResponses(deleteResponses);
    }
  }
}
