package io.harness.ci.execution;

import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;

import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.hsqs.client.HsqsServiceClient;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.UnAckRequest;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.queue.QueueController;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Slf4j
public class CIExecutionPoller implements Managed {
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject CIInitTaskMessageProcessor ciInitTaskMessageProcessor;

  private AtomicBoolean shouldStop = new AtomicBoolean(false);
  private static final int WAIT_TIME_IN_SECONDS = 5;
  private final String moduleName = "ci";
  private final int batchSize = 1;
  @Inject private HsqsServiceClient hsqsServiceClient;
  @Inject private QueueController queueController;

  @Override
  public void start() {
    log.info("Started the Consumer {}", this.getClass().getSimpleName());
    Thread.currentThread().setName("ci-init-task");

    try {
      do {
        while (getMaintenanceFlag()) {
          sleep(ofSeconds(1));
        }
        if (queueController.isNotPrimary()) {
          log.info(this.getClass().getSimpleName()
              + " is not running on primary deployment, will try again after some time...");
          TimeUnit.SECONDS.sleep(30);
          continue;
        }
        readEventsFrameworkMessages();
      } while (!Thread.currentThread().isInterrupted() && !shouldStop.get());
    } catch (Exception ex) {
      log.error("Consumer {} unexpectedly stopped", this.getClass().getSimpleName(), ex);
    } finally {
      log.info("finshed consuming messages for ci init task");
    }
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error("Events framework is down for " + this.getClass().getSimpleName() + " consumer. Retrying again...", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  @VisibleForTesting
  void pollAndProcessMessages() {
    try {
      Response<ResponseDTO<DequeueResponse[]>> messages = hsqsServiceClient
                                                              .dequeue(DequeueRequest.builder()
                                                                           .batchSize(batchSize)
                                                                           .consumerName(moduleName)
                                                                           .topic(moduleName)
                                                                           .maxWaitDuration(100)
                                                                           .build(),
                                                                  "sadlskd")
                                                              .execute();
      for (DequeueResponse message : messages.body().getData()) {
        processMessage(message);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void processMessage(DequeueResponse message) {
    log.info("Read message with message id {} from hsqs", message.getItemId());
    String authToken = ciExecutionServiceConfig.getQueueServiceToken();
    if (ciInitTaskMessageProcessor.processMessage(message)) {
      hsqsServiceClient.ack(AckRequest.builder().itemID(message.getItemId()).topic(moduleName).build(), authToken);
    } else {
      hsqsServiceClient.unack(UnAckRequest.builder().itemID(message.getItemId()).topic(moduleName).build(), authToken);
    }
  }

  @Override
  public void stop() throws Exception {}
}