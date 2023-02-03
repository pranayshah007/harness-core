/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.queue;

import io.harness.hsqs.client.HsqsServiceClient;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.hsqs.client.model.AckResponse;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.hsqs.client.model.UnAckRequest;
import io.harness.hsqs.client.model.UnAckResponse;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import retrofit2.Response;

@Slf4j
public class QueueClient {
  @Inject private HsqsServiceClient hsqsServiceClient;
  String topic = "ci";
  String moduleName = "ci";
  int batchSize = 5;
  public String queue(String accountId, String payload) throws IOException {
    EnqueueRequest enqueueRequest =
        EnqueueRequest.builder().topic(topic).subTopic(accountId).producerName(moduleName).payload(payload).build();
    Response<EnqueueResponse> execute = hsqsServiceClient.enqueue(enqueueRequest).execute();
    if (execute.code() == 200) {
      log.info("message queued. message id: {}", execute.body().getItemId());
      return execute.body().getItemId();
    } else {
      log.info("message queue failed. response code {}", execute.code());
      return "";
    }
  }

  public List<DequeueResponse> dequeue() throws IOException {
    Response<List<DequeueResponse>> messages = hsqsServiceClient
                                                   .dequeue(DequeueRequest.builder()
                                                                .batchSize(batchSize)
                                                                .consumerName(moduleName)
                                                                .topic(moduleName)
                                                                .maxWaitDuration(100)
                                                                .build())
                                                   .execute();
    if (messages.code() == 200) {
      if (CollectionUtils.isNotEmpty(messages.body())) {
        List<DequeueResponse> body = messages.body();
        List<String> messageIds = body.stream().map(b -> b.getItemId()).collect(Collectors.toList());
        log.info("{} messages dequeued successfully with Ids: {} from hsqs", body.size(), messageIds);
      } else {
        log.info("0 messages dequeued successfully from hsqs");
      }
    } else {
      log.info("dequeue failed. response code {}", messages.code());
    }
    return messages.body();
  }

  public void ack(String accountId, String messageId) throws IOException {
    Response<AckResponse> response =
        hsqsServiceClient.ack(AckRequest.builder().itemID(messageId).topic(moduleName).subTopic(accountId).build())
            .execute();
    log.info("ack response code: {}, messageId: {}", response.code(), messageId);
  }

  public void unack(String accountId, String messageId) throws IOException {
    Response<UnAckResponse> response =
        hsqsServiceClient.unack(UnAckRequest.builder().itemID(messageId).topic(moduleName).subTopic(accountId).build())
            .execute();
    log.info("unack response code: {}, messageId: {}", response.code(), messageId);
  }
}
