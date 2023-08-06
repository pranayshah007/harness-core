/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.outbox.OutboxSDKConstants.DEFAULT_MAX_EVENTS_POLLED;
import static io.harness.outbox.OutboxSDKConstants.DEFAULT_UNBLOCK_RETRY_INTERVAL_IN_MINUTES;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_EVENT_PROCESSING_TIME_METRIC_NAME;

import static java.time.Duration.ofMillis;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.filter.OutboxEventFilter;
import io.harness.outbox.monitor.OutboxMetricsServiceImpl;

import com.google.inject.Inject;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class OutboxEventPollJob implements Runnable {
  private final OutboxService outboxService;
  private final OutboxEventHandler outboxEventHandler;
  private final PersistentLocker persistentLocker;
  private final OutboxPollConfiguration outboxPollConfiguration;
  private final OutboxEventFilter outboxEventFilter;
  private final Retry retry;
  private static final String OUTBOX_POLL_JOB_LOCK = "OUTBOX_POLL_JOB_LOCK";
  private final String outboxLockId;
  private final OutboxMetricsServiceImpl outboxMetricsService;

  @Inject
  public OutboxEventPollJob(OutboxService outboxService, OutboxEventHandler outboxEventHandler,
      PersistentLocker persistentLocker, OutboxPollConfiguration outboxPollConfiguration,
      OutboxMetricsServiceImpl outboxMetricsService) {
    this.outboxService = outboxService;
    this.outboxEventHandler = outboxEventHandler;
    this.persistentLocker = persistentLocker;
    this.outboxPollConfiguration = outboxPollConfiguration;
    this.outboxMetricsService = outboxMetricsService;
    this.outboxLockId = OUTBOX_POLL_JOB_LOCK + "_" + this.outboxPollConfiguration.getLockId();
    this.outboxEventFilter = OutboxEventFilter.builder().maximumEventsPolled(DEFAULT_MAX_EVENTS_POLLED).build();
    RetryConfig retryConfig = RetryConfig.custom()
                                  .intervalFunction(IntervalFunction.ofExponentialBackoff(1000, 1.5))
                                  .maxAttempts(this.outboxPollConfiguration.getMaximumRetryAttemptsForAnEvent())
                                  .build();
    this.retry = Retry.of("outboxEventHandleRetry", retryConfig);
  }

  @Override
  public void run() {
    try {
      if (!getMaintenanceFlag()) {
        pollAndHandleOutboxEvents();
      }
    } catch (Exception exception) {
      log.error("Unexpected error occurred during the execution of OutboxPollJob", exception);
    }
  }

  private void pollAndHandleOutboxEvents() {
    try (AcquiredLock<?> lock = persistentLocker.tryToAcquireLock(outboxLockId, Duration.ofMinutes(2))) {
      if (lock == null) {
        log.warn("Could not acquire lock for outbox poll job");
        return;
      }
      List<OutboxEvent> outboxEvents;
      try {
        outboxEvents = outboxService.list(outboxEventFilter);
      } catch (InstantiationError error) {
        log.error("InstantiationError occurred while fetching entries from the outbox", error);
        return;
      }

      for (int i = 0; i < outboxEvents.size() && !Thread.currentThread().isInterrupted(); i++) {
        OutboxEvent outbox = outboxEvents.get(i);
        long startTime = System.currentTimeMillis();
        long outboxEventWaitingTime = startTime - outbox.getCreatedAt();
        boolean success = handle(outbox);
        long outboxEventProcessingTime = System.currentTimeMillis() - startTime;
        log.info(String.format(
            "[OutboxEventPollJob] id: %s, eventType: %s, resourceType: %s, waitingTime: %d, processingTime: %d",
            outbox.getId(), outbox.getEventType(), outbox.getResource().getType(), outboxEventWaitingTime,
            outboxEventProcessingTime));
        outboxMetricsService.recordMetricsWithDuration(outbox.getResource().getType(),
            ofMillis(outboxEventProcessingTime), OUTBOX_EVENT_PROCESSING_TIME_METRIC_NAME);
        try {
          if (success) {
            outboxService.delete(outbox.getId());
          } else {
            outbox.setBlocked(true);
            outbox.setNextUnblockAttemptAt(
                Instant.now().plus(DEFAULT_UNBLOCK_RETRY_INTERVAL_IN_MINUTES, ChronoUnit.MINUTES));
            outboxService.update(outbox);
          }
        } catch (Exception exception) {
          log.error(String.format("Error occurred in post handling of outbox event with id %s and type %s",
                        outbox.getId(), outbox.getEventType()),
              exception);
        }
      }
    }
  }

  private boolean handle(OutboxEvent outboxEvent) {
    boolean success = false;
    try {
      success = outboxEventHandler.handle(outboxEvent);
    } catch (Exception exception) {
      log.error(String.format("Error occurred while handling outbox event with id %s and type %s", outboxEvent.getId(),
                    outboxEvent.getEventType()),
          exception);
    }
    if (!success && !Boolean.TRUE.equals(outboxEvent.getBlocked())) {
      log.error("Retrying this outbox event with exponential backoff now...");
      success = handleWithExponentialBackOff(outboxEvent);
    }
    return success;
  }

  private boolean handleWithExponentialBackOff(OutboxEvent outboxEvent) {
    try {
      return retry.executeSupplier(() -> {
        if (!outboxEventHandler.handle(outboxEvent)) {
          throw new UnexpectedException(String.format(
              "Outbox event handling failed in another retry attempt for event with id %s", outboxEvent.getId()));
        }
        return true;
      });
    } catch (Exception exception) {
      log.error(String.format("Error occurred while handling outbox event with id %s and type %s", outboxEvent.getId(),
                    outboxEvent.getEventType()),
          exception);
      return false;
    }
  }
}
