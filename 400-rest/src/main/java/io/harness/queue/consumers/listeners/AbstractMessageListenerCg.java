/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.queue.consumers.listeners;

import io.harness.eventsframework.consumer.Message;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;
import io.harness.waiter.NotifyEventListenerHelper;
import io.harness.waiter.notify.NotifyEventProto;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.NonNull;

public abstract class AbstractMessageListenerCg implements MessageListener {
  @Inject NotifyEventListenerHelper notifyEventListenerHelper;

  @Override
  public boolean handleMessage(Message message) {
    NotifyEventProto event = extractEntity(message);
    notifyEventListenerHelper.onMessage(event.getWaitInstanceId());
    return true;
  }

  @VisibleForTesting
  NotifyEventProto extractEntity(@NonNull Message message) {
    try {
      return NotifyEventProto.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException ex) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking NotifyEventProto for key %s", message.getId()), ex);
    }
  }
}
