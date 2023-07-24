/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.queuePoller;

import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.beans.HsqsDequeueConfig;
import io.harness.hsqs.client.beans.HsqsProcessMessageResponse;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.UnAckRequest;

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
@OwnedBy(HarnessTeam.PIPELINE)
public abstract class AbstractHsqsQueueProcessor implements Managed {
  private AtomicBoolean shouldStop = new AtomicBoolean(false);
  @Inject HsqsClientService hsqsClientService;

  private static final int QUEUE_FETCH_WAIT_TIME_SECONDS = 1;
  private static final int MAINTENANCE_FLAG_SLEEP_SECONDS = 10;
  private static final int THREAD_SLEEP_TIME_IN_MILLIS = 300;

  @Override
  public void start() {
    ExecutorService executorService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat(getTopicName() + "-queue-poller").build());
    executorService.execute(this::run);
  }

  public void run() {
    log.info("Started the Consumer {} for {}", this.getClass().getSimpleName(), getTopicName());

    try {
      do {
        while (getMaintenanceFlag()) {
          sleep(ofSeconds(MAINTENANCE_FLAG_SLEEP_SECONDS));
        }
        readEventsFrameworkMessages();
      } while (!Thread.currentThread().isInterrupted() && !shouldStop.get());
    } catch (Exception ex) {
      log.error("hsqs Consumer unexpectedly stopped", ex);
    } finally {
      log.info("finished consuming messages for {} init task", getTopicName());
    }
  }

  private void readEventsFrameworkMessages() {
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
                                                                     .batchSize(getHsqsDequeueConfig().getBatchSize())
                                                                     .consumerName(getTopicName())
                                                                     .topic(getTopicName())
                                                                     .maxWaitDuration(QUEUE_FETCH_WAIT_TIME_SECONDS)
                                                                     .build());
      for (DequeueResponse message : messages) {
        processMessage(message);
      }
      sleepBeforeReadingNextBatch(messages);
    } catch (Exception e) {
      log.error("Error in poll and process messages", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void stop() throws Exception {}

  private void processMessage(DequeueResponse message) {
    log.info("Read message with message id {} from hsqs", message.getItemId());
    HsqsProcessMessageResponse processMessageResponse = processResponse(message);
    try {
      if (processMessageResponse.getSuccess()) {
        hsqsClientService.ack(AckRequest.builder()
                                  .itemId(message.getItemId())
                                  .topic(getTopicName())
                                  .subTopic(processMessageResponse.getAccountId())
                                  .consumerName(getTopicName())
                                  .build());
      } else {
        UnAckRequest unAckRequest = UnAckRequest.builder()
                                        .itemId(message.getItemId())
                                        .topic(getTopicName())
                                        .subTopic(processMessageResponse.getAccountId())
                                        .build();
        hsqsClientService.unack(unAckRequest);
      }
    } catch (Exception ex) {
      log.error("got error in calling hsqs client for message id: {}", message.getItemId(), ex);
    }
  }

  private void sleepBeforeReadingNextBatch(List<DequeueResponse> messages) {
    try {
      if (messages.size() < getHsqsDequeueConfig().getBatchSize()) {
        TimeUnit.MILLISECONDS.sleep(THREAD_SLEEP_TIME_IN_MILLIS);
      } else {
        TimeUnit.MILLISECONDS.sleep(getHsqsDequeueConfig().getThreadSleepTimeInMillis());
      }
    } catch (InterruptedException ex) {
      log.error("could not sleep the thread due to an exception", ex);
    }
  }

  public abstract HsqsProcessMessageResponse processResponse(DequeueResponse message);

  public abstract String getTopicName();

  public abstract HsqsDequeueConfig getHsqsDequeueConfig();
}
