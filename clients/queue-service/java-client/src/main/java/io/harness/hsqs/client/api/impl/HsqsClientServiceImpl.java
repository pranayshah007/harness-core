/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.hsqs.client.api.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.hsqs.client.HsqsClient;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.beans.AckRequestLogContext;
import io.harness.hsqs.client.beans.DequeueRequestLogContext;
import io.harness.hsqs.client.beans.EnqueueRequestLogContext;
import io.harness.hsqs.client.beans.UnAckRequestLogContext;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.hsqs.client.model.AckResponse;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.hsqs.client.model.HsqsClientConstants;
import io.harness.hsqs.client.model.UnAckRequest;
import io.harness.hsqs.client.model.UnAckResponse;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class HsqsClientServiceImpl implements HsqsClientService {
  @Inject HsqsClient hsqsClient;
  @Inject @Named(HsqsClientConstants.QUEUE_SERVICE_ENV_NAMESPACE) String queueEnvNamespace;

  @Override
  public EnqueueResponse enqueue(EnqueueRequest enqueueRequest) {
    EnqueueRequest modifiedRequest = enqueueRequest.withTopic(getTopicName(enqueueRequest.getTopic()));
    try (EnqueueRequestLogContext context = new EnqueueRequestLogContext(modifiedRequest)) {
      EnqueueResponse enqueueResponse = NGRestUtils.getGeneralResponse(hsqsClient.enqueue(modifiedRequest));
      log.info("Enqueue request sent with messageId: {}", enqueueResponse.getItemId());
      return enqueueResponse;
    }
  }

  @Override
  public List<DequeueResponse> dequeue(DequeueRequest dequeueRequest) {
    DequeueRequest modifiedRequest = dequeueRequest.withTopic(getTopicName(dequeueRequest.getTopic()));
    try (DequeueRequestLogContext context = new DequeueRequestLogContext(modifiedRequest)) {
      List<DequeueResponse> dequeueResponses = NGRestUtils.getGeneralResponse(hsqsClient.dequeue(modifiedRequest));
      log.info("Dequeue response received for messageList of size: {}", dequeueResponses.size());
      return dequeueResponses;
    }
  }

  @Override
  public AckResponse ack(AckRequest ackRequest) {
    AckRequest modifiedRequest = ackRequest.withTopic(getTopicName(ackRequest.getTopic()));
    try (AckRequestLogContext context = new AckRequestLogContext(modifiedRequest)) {
      AckResponse ackResponse = NGRestUtils.getGeneralResponse(hsqsClient.ack(modifiedRequest));
      log.info("Ack response send for messageId: {}", ackResponse.getItemId());
      return ackResponse;
    }
  }

  @Override
  public UnAckResponse unack(UnAckRequest unAckRequest) {
    UnAckRequest modifiedRequest = unAckRequest.withTopic(getTopicName(unAckRequest.getTopic()));
    try (UnAckRequestLogContext context = new UnAckRequestLogContext(modifiedRequest)) {
      UnAckResponse unAckResponse = NGRestUtils.getGeneralResponse(hsqsClient.unack(modifiedRequest));
      log.info("Unack Request sent for topicName {}, subTopic {} and itemId {}", modifiedRequest.getTopic(),
          modifiedRequest.getSubTopic(), modifiedRequest.getItemId());
      return unAckResponse;
    }
  }

  /**
   * it Pre-append envNameSpace to topic to differentiate topics going to same queue from different env
   * @param givenTopicName
   * @return
   */
  private String getTopicName(String givenTopicName) {
    return (queueEnvNamespace.isEmpty() ? "" : queueEnvNamespace + ":") + "streams:" + givenTopicName;
  }
}
