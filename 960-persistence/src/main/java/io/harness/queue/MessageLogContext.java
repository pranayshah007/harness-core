/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.queue;

import io.harness.data.structure.NullSafeImmutableMap;
import io.harness.logging.AutoLogContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageLogContext extends AutoLogContext {
  public static final String MESSAGE_CLASS = "messageClass";
  public static final String MESSAGE_ID = "messageId";
  public static final String MESSAGE_TOPIC = "messageTopic";

  public MessageLogContext(Queuable message, OverrideBehavior behavior) {
    super(NullSafeImmutableMap.<String, String>builder()
              .put(MESSAGE_CLASS, message.getClass().getName())
              .putIfNotNull(MESSAGE_ID, message.getId())
              .putIfNotNull(MESSAGE_TOPIC, message.getTopic())
              .build(),
        behavior);
  }
}
