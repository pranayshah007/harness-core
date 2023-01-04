/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.queue;

import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.states.V1.InitializeTaskStepV2;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.hsqs.client.HsqsServiceClient;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.hsqs.client.model.AckResponse;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.UnAckRequest;
import io.harness.hsqs.client.model.UnAckResponse;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;
import retrofit2.Response;

@Slf4j
public class CIExecutionPoller implements Managed {
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject CIInitTaskMessageProcessor ciInitTaskMessageProcessor;
  @Inject HsqsServiceClient hsqsServiceClient;
  @Inject InitializeTaskStepV2 initializeTaskStepV2;
  @Inject AsyncWaitEngine asyncWaitEngine;
  private AtomicBoolean shouldStop = new AtomicBoolean(false);
  private static final int WAIT_TIME_IN_SECONDS = 5;
  private final String moduleName = "ci";
  private final int batchSize = 1;
  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 3;

  @Override
  public void start() {
    log.info("Started the Consumer {}", this.getClass().getSimpleName());
    Thread.currentThread().setName("ci-init-task");

    try {
      do {
        while (getMaintenanceFlag()) {
          sleep(ofSeconds(1));
        }
        readEventsFrameworkMessages();
      } while (!Thread.currentThread().isInterrupted() && !shouldStop.get());
    } catch (Exception ex) {
      log.error("Consumer {} unexpectedly stopped", this.getClass().getSimpleName(), ex);
    } finally {
      log.info("finished consuming messages for ci init task");
    }
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error("Events framework is down for " + this.getClass().getSimpleName() + " consumer. Retrying again...", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    } catch (Exception ex) {
      log.error("got error while reading messages from hsqs " + this.getClass().getSimpleName()
              + " consumer. Retrying again...",
          ex);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  @VisibleForTesting
  void pollAndProcessMessages() {
    try {
      Response<List<DequeueResponse>> messages =
          hsqsServiceClient
              .dequeue(DequeueRequest.builder()
                           .batchSize(batchSize)
                           .consumerName(moduleName)
                           .topic(moduleName)
                           .maxWaitDuration(100)
                           .build(),
                  ciExecutionServiceConfig.getQueueServiceClient().getAuthToken())
              .execute();
      for (DequeueResponse message : messages.body()) {
        processMessage(message);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void stop() throws Exception {}

  private void processMessage(DequeueResponse message) {
    log.info("Read message with message id {} from hsqs", message.getItemId());
    String authToken = ciExecutionServiceConfig.getQueueServiceClient().getAuthToken();

    RetryPolicy<Object> retryPolicy =
        getRetryPolicy(format("[Retrying failed call to hsqs: {}"), format("Failed to call hsqs retrying {} times"));

    try {
      ProcessMessageResponse processMessageResponse = ciInitTaskMessageProcessor.processMessage(message);
      if (processMessageResponse.getSuccess()) {
        Response<AckResponse> response = hsqsServiceClient
                                             .ack(AckRequest.builder()
                                                      .itemID(message.getItemId())
                                                      .topic(moduleName)
                                                      .subTopic(processMessageResponse.getAccountId())
                                                      .build(),
                                                 authToken)
                                             .execute();
        log.info("ack response code: {}", response.code());
      } else {
        Response<UnAckResponse> response = hsqsServiceClient
                                               .unack(UnAckRequest.builder()
                                                          .itemID(message.getItemId())
                                                          .topic(moduleName)
                                                          .subTopic(processMessageResponse.getAccountId())
                                                          .build(),
                                                   authToken)
                                               .execute();
        log.info("unack response code: {}", response.code());
      }
    } catch (Exception ex) {
      log.error("got error in calling hsqs client", ex);
    }
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}