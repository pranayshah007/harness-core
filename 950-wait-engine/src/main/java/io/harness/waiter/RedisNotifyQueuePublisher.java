/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.waiter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.waiter.notify.NotifyEventProto;

@OwnedBy(HarnessTeam.PIPELINE)
public abstract class RedisNotifyQueuePublisher implements NotifyQueuePublisher {
  private final Producer producer;

  public RedisNotifyQueuePublisher(Producer producer) {
    this.producer = producer;
  }

  @Override
  public void send(NotifyEvent payload) {
    producer.send(Message.newBuilder().setData(toProto(payload).toByteString()).build());
  }

  public NotifyEventProto toProto(NotifyEvent event) {
    return NotifyEventProto.newBuilder().setWaitInstanceId(event.getWaitInstanceId()).build();
  }
}
