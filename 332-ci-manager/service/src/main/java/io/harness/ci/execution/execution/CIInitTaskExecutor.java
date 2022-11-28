package io.harness.ci.execution;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.hsqs.client.HsqsServiceClient;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.queue.QueueController;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.threading.Morpheus.sleep;
import static java.time.Duration.ofSeconds;

@Slf4j
public class CIInitTaskExecutor implements Runnable{
    private AtomicBoolean shouldStop = new AtomicBoolean(false);
    private static final int WAIT_TIME_IN_SECONDS = 10;
    private final String moduleName = "ci";
    private final int batchSize = 1;
    @Inject private HsqsServiceClient hsqsServiceClient;
    @Inject private QueueController queueController;

    @Override
    public void run() {
        log.info("Started the Consumer {}", this.getClass().getSimpleName());
        String threadName = this.getClass().getSimpleName() + "-handler-" + generateUuid();
        log.debug("Setting thread name to {}", threadName);
        Thread.currentThread().setName(threadName);

        try {
            preThreadHandler();
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
            postThreadCompletion();
        }
    }

    public void preThreadHandler() {}

    public void postThreadCompletion() {}

    protected void readEventsFrameworkMessages() throws InterruptedException {
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
                String messageId = message.getItemId();
                boolean messageProcessed = processMessage(message);
                if (messageProcessed) {
                    hsqsServiceClient.ack(AckRequest.builder().itemID(messageId).build(), "");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected boolean processMessage(DequeueResponse message) {
        AtomicBoolean success = new AtomicBoolean(true);
        //        if (messageListener.isProcessable(message) && !isAlreadyProcessed(message)) {
        log.debug("Read message with message id {} from hsqs", message.getItemId());
        //            insertMessageInCache(message);
        if (!messageListener.handleMessage(message)) {
            success.set(false);
        } else {
            hsqsServiceClient.ack(
                    AckRequest.builder().itemID(message.getItemId()).topic("").subTopic("").build(), "auth_key");
        }
        //        }
        return success.get();
    }
}
