package io.harness.queueservice;

import com.google.api.client.util.Base64;
import io.harness.beans.DelegateTask;
import io.harness.hsqs.client.HsqsServiceClient;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.queueservice.config.DelegateQueueServiceConfig;
import io.harness.queueservice.infc.DelegateServiceQueue;

import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;

public class DelegateTaskQueueService implements DelegateServiceQueue<DelegateTask> {
  @Inject private HsqsServiceClient hsqsServiceClient;
  @Inject private DelegateQueueServiceConfig delegateQueueServiceConfig;

  @Inject DelegateTaskServiceClassic delegateTaskServiceClassic;

  @Override
  public void enqueue(DelegateTask delegateTask) {
    String topic = delegateQueueServiceConfig.getTopic();
    byte[] payload = RecastOrchestrationUtils.toBytes(delegateTask);
    EnqueueRequest enqueueRequest = EnqueueRequest.builder()
                                        .topic(topic)
                                        .subTopic(delegateTask.getAccountId())
                                        .producerName(topic)
                                        .payload(Base64.encodeBase64String(payload))
                                        .build();
    hsqsServiceClient.enqueue(enqueueRequest, delegateQueueServiceConfig.getQueueServiceToken());
  }

  @Override
  public <T> Object dequeue() throws IOException {
    DequeueRequest dequeueRequest =
        DequeueRequest.builder().batchSize(10).consumerName(delegateQueueServiceConfig.getTopic()).build();
    List<DequeueResponse> dequeueResponses =
        hsqsServiceClient.dequeue(dequeueRequest, delegateQueueServiceConfig.getQueueServiceToken()).execute().body();
    return null;
  }
}
