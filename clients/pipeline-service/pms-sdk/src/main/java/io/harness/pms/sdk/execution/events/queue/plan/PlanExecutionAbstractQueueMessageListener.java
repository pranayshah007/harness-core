/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.execution.events.queue.plan;

import io.harness.eventsframework.consumer.Message;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.ng.core.event.MessageListener;
import io.harness.pms.events.base.PmsAbstractMessageListener;

import java.util.concurrent.ExecutorService;

public abstract class PlanExecutionAbstractQueueMessageListener {
  public abstract boolean handleMessage(DequeueResponse message);
}
