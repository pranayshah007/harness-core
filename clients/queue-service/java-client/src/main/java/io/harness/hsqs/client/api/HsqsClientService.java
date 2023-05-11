/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.hsqs.client.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.hsqs.client.api.impl.HsqsClientServiceImpl;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.hsqs.client.model.AckResponse;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.hsqs.client.model.UnAckRequest;
import io.harness.hsqs.client.model.UnAckResponse;

import com.google.inject.ImplementedBy;
import java.util.List;
import retrofit2.http.Body;

/**
 * ENV is appended to topic so that if even same queue BE is used across multiple envs, it should lead to consumers from
 * across envs to consumer
 */
@OwnedBy(PIPELINE)
@ImplementedBy(HsqsClientServiceImpl.class)
public interface HsqsClientService {
  // Enqueues given payload to given topic
  EnqueueResponse enqueue(@Body EnqueueRequest enqueueRequest);

  // Dequeue events from given topic
  List<DequeueResponse> dequeue(@Body DequeueRequest dequeueRequest);

  // Acknowledges given itemId for a topic so that item is removed from the queue
  AckResponse ack(@Body AckRequest ackRequest);

  // If itemId is not given, then for given topic and subtopic, the events are blocked for a given
  // retryTimeAfterDuration time (in sec)
  UnAckResponse unack(@Body UnAckRequest unAckRequest);
}
