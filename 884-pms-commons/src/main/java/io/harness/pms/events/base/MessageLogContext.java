/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.events.base;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.logging.logcontext.AutoLogContext;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class MessageLogContext extends AutoLogContext {
  public MessageLogContext(Message message) {
    super(buildLogContext(message), OverrideBehavior.OVERRIDE_NESTS);
  }

  private static Map<String, String> buildLogContext(Message message) {
    Map<String, String> contextMap = new HashMap<>(message.getMessage().getMetadataMap());
    contextMap.put("messageId", message.getId());
    return contextMap;
  }
}
