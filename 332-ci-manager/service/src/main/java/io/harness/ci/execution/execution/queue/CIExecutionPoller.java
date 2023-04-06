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

import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.states.V1.InitializeTaskStepV2;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.UnAckRequest;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CIExecutionPoller implements Managed {
  @Inject CIInitTaskMessageProcessor ciInitTaskMessageProcessor;
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject HsqsClientService hsqsClientService;
  @Inject InitializeTaskStepV2 initializeTaskStepV2;
  @Inject AsyncWaitEngine asyncWaitEngine;
  private AtomicBoolean shouldStop = new AtomicBoolean(false);
  private static final int WAIT_TIME_IN_SECONDS = 5;
  private final int batchSize = 5;

  @Override
  public void start() {
    ExecutorService executorService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat(getModuleName() + "-queue-poller").build());
    executorService.execute(this::run);
  }
  public void run() {
    log.info("Started the Consumer {} for {}", this.getClass().getSimpleName(), this.getModuleName());

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
      log.info("finished consuming messages for {} init task", this.getModuleName());
    }
  }

  private String getModuleName() {
    return ciExecutionServiceConfig.getQueueServiceClientConfig().getTopic();
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (Exception ex) {
      log.error("got error while reading messages from hsqs consumer. Retrying again...", ex);
    }
  }

  @VisibleForTesting
  void pollAndProcessMessages() {
    try {
      List<DequeueResponse> messages = hsqsClientService.dequeue(DequeueRequest.builder()
                                                                     .batchSize(batchSize)
                                                                     .consumerName(this.getModuleName())
                                                                     .topic(this.getModuleName())
                                                                     .maxWaitDuration(100)
                                                                     .build());
      for (DequeueResponse message : messages) {
        processMessage(message);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void stop() throws Exception {}

  private void processMessage(DequeueResponse message) {
    log.info("Read message with message id {} from hsqs", message.getItemId());
    ProcessMessageResponse processMessageResponse = ciInitTaskMessageProcessor.processMessage(message);
    try {
      if (processMessageResponse.getSuccess()) {
        hsqsClientService.ack(AckRequest.builder()
                                  .itemId(message.getItemId())
                                  .topic(this.getModuleName())
                                  .subTopic(processMessageResponse.getAccountId())
                                  .consumerName(this.getModuleName())
                                  .build());
      } else {
        UnAckRequest unAckRequest = UnAckRequest.builder()
                                        .itemId(message.getItemId())
                                        .topic(this.getModuleName())
                                        .subTopic(processMessageResponse.getAccountId())
                                        .build();
        hsqsClientService.unack(unAckRequest);
      }
    } catch (Exception ex) {
      log.error("got error in calling hsqs client for message id: {}", message.getItemId(), ex);
    }
  }
}