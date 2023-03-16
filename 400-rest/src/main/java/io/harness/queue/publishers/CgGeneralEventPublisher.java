/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.queue.publishers;

import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.waiter.NotifyEvent;
import io.harness.waiter.RedisNotifyQueuePublisher;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CgGeneralEventPublisher extends RedisNotifyQueuePublisher {
  @Inject
  public CgGeneralEventPublisher(@Named(EventsFrameworkConstants.CG_GENERAL_EVENT) Producer producer) {
    super(producer);
  }

  @Override
  public void send(NotifyEvent payload) {
    super.send(payload);
    log.info("Notification event produced for by {}, for waitInstanceId {}", this.getClass().getSimpleName(),
        payload.getWaitInstanceId());
  }
}
