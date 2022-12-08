package io.harness.delegate.queueservice;

import static java.util.stream.Collectors.toList;

import io.harness.beans.DelegateTask;
import io.harness.hsqs.client.HsqsServiceClient;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.hsqs.client.model.AckResponse;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.queueservice.DelegateTaskDequeue;
import io.harness.queueservice.config.DelegateQueueServiceConfig;
import io.harness.queueservice.infc.DelegateServiceQueue;

import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelegateTaskQueueService implements DelegateServiceQueue<DelegateTask> {
  @Inject private HsqsServiceClient hsqsServiceClient;
  @Inject private DelegateQueueServiceConfig delegateQueueServiceConfig;
  @Inject private DelegateTaskServiceClassic delegateTaskServiceClassic;
  private ObjectMapper objectMapper;

  public DelegateTaskQueueService(HsqsServiceClient hsqsServiceClient,
      DelegateQueueServiceConfig delegateQueueServiceConfig, ObjectMapper objectMapper) {
    this.hsqsServiceClient = hsqsServiceClient;
    this.delegateQueueServiceConfig = delegateQueueServiceConfig;
    this.objectMapper = objectMapper;
  }

  @Override
  public void enqueue(DelegateTask delegateTask) throws IOException {
    String topic = delegateQueueServiceConfig.getTopic();
    String task =
        java.util.Base64.getEncoder().encodeToString(objectMapper.writeValueAsString(delegateTask).getBytes());
    EnqueueRequest enqueueRequest = EnqueueRequest.builder()
                                        .topic(topic)
                                        .payload(task)
                                        .subTopic(delegateTask.getAccountId())
                                        .producerName(topic)
                                        .build();
    hsqsServiceClient.enqueue(enqueueRequest, "sampleToken");
  }

  @Override
  public <T> Object dequeue() throws IOException {
    DequeueRequest dequeueRequest = DequeueRequest.builder()
                                        .batchSize(100)
                                        .consumerName(delegateQueueServiceConfig.getTopic())
                                        .topic(delegateQueueServiceConfig.getTopic())
                                        .build();
    List<DequeueResponse> dequeueResponses = hsqsServiceClient.dequeue(dequeueRequest, "sampleToken").execute().body();
    List<DelegateTaskDequeue> delegateTasksDequeueList =
        dequeueResponses.stream()
            .map(dequeueResponse
                -> DelegateTaskDequeue.builder()
                       .payload(dequeueResponse.getPayload())
                       .itemId(dequeueResponse.getItemId())
                       .delegateTask(convertToDelegateTask(dequeueResponse.getPayload(), objectMapper).orElse(null))
                       .build())
            .filter(this::isResourceAvailableToAssignTask)
            .collect(toList());
    delegateTasksDequeueList.forEach(this::acknowledgeAndProcessDelegateTask);
    return true;
  }

  @Override
  public String acknowledge(String itemId, String accountId) throws IOException {
    AckResponse response =
        hsqsServiceClient
            .ack(AckRequest.builder().consumerName(delegateQueueServiceConfig.getTopic()).subTopic(accountId).build(),
                "sampleToken")
            .execute()
            .body();
    return response != null ? response.getItemID() : "";
  }

  private boolean isResourceAvailableToAssignTask(DelegateTaskDequeue delegateTaskDequeue) {
    return true;
  }

  private void acknowledgeAndProcessDelegateTask(DelegateTaskDequeue delegateTaskDequeue) {
    try {
      if (delegateTaskDequeue.getDelegateTask() != null
          && delegateTaskServiceClassic.saveAndBroadcastDelegateTask(delegateTaskDequeue.getDelegateTask())) {
        acknowledge(delegateTaskDequeue.getItemId(), delegateTaskDequeue.getDelegateTask().getAccountId());
      }
    } catch (Exception e) {
      log.error("Unable to acknowledge queue service on dequeue delegate task", e);
    }
  }

  public Optional<DelegateTask> convertToDelegateTask(String payload, ObjectMapper objectMapper) {
    try {
      return Optional.ofNullable(objectMapper.readValue(Base64.getDecoder().decode(payload), DelegateTask.class));
    } catch (Exception e) {
      log.error("Error while decoding delegate task from queue. ", e);
    }
    return Optional.empty();
  }
}
