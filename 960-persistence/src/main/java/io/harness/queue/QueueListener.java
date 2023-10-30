/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.queue;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.manage.GlobalContextManager.initGlobalContextGuard;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofSeconds;

import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.logging.DelayLogContext;
import io.harness.logging.ProcessTimeLogContext;
import io.harness.manage.GlobalContextManager.GlobalContextGuard;
import io.harness.queue.QueueConsumer.Filter;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class QueueListener<T extends Queuable> implements Runnable {
  @Setter private boolean runOnce;
  private final boolean primaryOnly;

  private AtomicBoolean shouldStop = new AtomicBoolean(false);

  @Inject private TimerScheduledExecutorService timer;
  @Inject private QueueController queueController;

  @Getter @Setter private QueueConsumer<T> queueConsumer;

  public QueueListener(QueueConsumer<T> queueConsumer, boolean primaryOnly) {
    this.queueConsumer = queueConsumer;
    this.primaryOnly = primaryOnly;
  }

  @Override
  public void run() {
    String threadName = queueConsumer.getName() + "-handler-" + generateUuid();
    log.debug("Setting thread name to {}", threadName);
    Thread.currentThread().setName(threadName);

    do {
      while (getMaintenanceFlag() || (primaryOnly && queueController.isNotPrimary())) {
        sleep(ofSeconds(5));
      }

      if (!execute()) {
        break;
      }

    } while (!runOnce && !shouldStop.get());
  }

  public boolean execute() {
    if (log.isDebugEnabled()) {
      log.debug("Total event in running: [{}] and not running:[{}] - Class info [{}]",
          queueConsumer.count(Filter.RUNNING), queueConsumer.count(Filter.NOT_RUNNING), this);
    }
    T message = null;
    try {
      log.trace("Waiting for message");
      message = queueConsumer.get(ofSeconds(3), ofSeconds(1));
    } catch (Exception exception) {
      if (exception.getCause() instanceof InterruptedException) {
        log.info("Thread interrupted, shutting down for queue {}", queueConsumer.getName());
        return false;
      }
      log.error("Exception happened while fetching message from queue {}", queueConsumer.getName(), exception);
    }

    if (message != null) {
      log.debug("Consuming message [{}]", message);
      processMessage(message);
    }
    return true;
  }

  public void pumpAll() {
    while (true) {
      T message = null;
      try {
        log.trace("Waiting for message");
        message = queueConsumer.get(Duration.ZERO, Duration.ZERO);
      } catch (Exception exception) {
        if (exception.getCause() instanceof InterruptedException) {
          log.info("Thread interrupted, shutting down for queue {}", queueConsumer.getName());
          return;
        }
        log.error("Exception happened while fetching message from queue {}", queueConsumer.getName(), exception);
      }

      if (message == null) {
        break;
      }
      processMessage(message);
    }
  }

  @SuppressWarnings({"PMD", "squid:S1181"})
  private void processMessage(T message) {
    long startTime = currentTimeMillis();

    try (MessageLogContext ignore = new MessageLogContext(message, OVERRIDE_ERROR)) {
      long timerInterval = queueConsumer.heartbeat().toMillis() - 500;
      final T finalizedMessage = message;
      ScheduledFuture<?> future = timer.scheduleAtFixedRate(
          () -> queueConsumer.updateHeartbeat(finalizedMessage), timerInterval, timerInterval, TimeUnit.MILLISECONDS);

      try (GlobalContextGuard guard = initGlobalContextGuard(message.getGlobalContext())) {
        long delay = startTime - message.getEarliestGet().toInstant().toEpochMilli();
        try (DelayLogContext ignore2 = new DelayLogContext(delay, OVERRIDE_ERROR)) {
          log.debug("Working on message");
        }

        onMessage(message);
      } finally {
        future.cancel(true);
      }

      queueConsumer.ack(message);
    } catch (InstantiationError exception) {
      log.error("Critical exception happened in onMessage {}", queueConsumer.getName(), exception);
      queueConsumer.ack(message);
    } catch (Throwable exception) {
      onException(exception, message);
    } finally {
      long processTime = currentTimeMillis() - startTime;
      try (ProcessTimeLogContext ignore2 = new ProcessTimeLogContext(processTime, OVERRIDE_ERROR)) {
        log.debug("Done with message");
      } catch (Throwable exception) {
        log.error("Exception while recording the processing of message", exception);
      }
    }
  }

  public abstract void onMessage(T message);

  protected void requeue(T message) {
    queueConsumer.requeue(message.getId(), message.getRetries() - 1);
  }

  public void onException(Throwable exception, T message) {
    if (exception instanceof WingsException) {
      ExceptionLogger.logProcessedMessages((WingsException) exception, MANAGER, log);
    } else {
      log.error("Exception happened while processing message " + message, exception);
    }

    if (message.getRetries() > 0) {
      requeue(message);
    } else {
      log.error("Out of retries for message " + message, exception);
      queueConsumer.ack(message);
    }
  }

  public void shutDown() {
    shouldStop.set(true);
  }
}
