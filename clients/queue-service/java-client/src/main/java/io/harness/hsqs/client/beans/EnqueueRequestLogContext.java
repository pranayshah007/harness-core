/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.hsqs.client.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.logging.AutoLogContext;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(PIPELINE)
public class EnqueueRequestLogContext extends AutoLogContext {
  public EnqueueRequestLogContext(EnqueueRequest enqueueRequest) {
    super(buildLogContext(enqueueRequest), OverrideBehavior.OVERRIDE_NESTS);
  }

  private static Map<String, String> buildLogContext(EnqueueRequest enqueueRequest) {
    Map<String, String> contextMap = new HashMap<>();
    contextMap.put("producerName", enqueueRequest.getProducerName());
    contextMap.put("subTopic", enqueueRequest.getSubTopic());
    contextMap.put("topic", enqueueRequest.getTopic());
    return contextMap;
  }
}
