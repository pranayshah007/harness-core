/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.queue;

import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;

import io.harness.hsqs.client.model.DequeueResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CIExecutionPoller implements Managed {
  @Inject CIInitTaskMessageProcessor ciInitTaskMessageProcessor;
  @Inject QueueClient queueClient;
  private AtomicBoolean shouldStop = new AtomicBoolean(false);
  private static final int WAIT_TIME_IN_SECONDS = 5;
  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);

  @Override
  public void start() {
    ExecutorService executorService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("ci-queue-poller").build());
    executorService.execute(this::run);
  }
  public void run() {
    log.info("Started the Consumer {}", this.getClass().getSimpleName());

    try {
      do {
        while (getMaintenanceFlag()) {
          sleep(ofSeconds(1));
        }
        readEventsFrameworkMessages();
        TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
      } while (!Thread.currentThread().isInterrupted() && !shouldStop.get());
    } catch (Exception ex) {
      log.error("hsqs Consumer unexpectedly stopped", ex);
    } finally {
      log.info("finished consuming messages for ci init task");
    }
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (Exception ex) {
      log.error("got error while reading messages from hsqs consumer. Retrying again...", ex);
    }
  }

  @VisibleForTesting
  void pollAndProcessMessages() throws IOException {
    List<DequeueResponse> messages = queueClient.dequeue();
    if (messages != null) {
      for (DequeueResponse message : messages) {
        processMessage(message);
      }
    }
  }

  @Override
  public void stop() throws Exception {}

  private void processMessage(DequeueResponse message) {
    ProcessMessageResponse processMessageResponse = ciInitTaskMessageProcessor.processMessage(message);
    try {
      if (processMessageResponse.getSuccess()) {
        queueClient.ack(processMessageResponse.getAccountId(), message.getItemId());
      } else {
        queueClient.unack(processMessageResponse.getAccountId(), message.getItemId());
      }
    } catch (Exception ex) {
      log.error("got error in calling hsqs client for message id: {}", message.getItemId(), ex);
    }
  }
}