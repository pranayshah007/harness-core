/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
public class PMSOutboxEventHandler implements OutboxEventHandler {
  private final Map<String, OutboxEventHandler> outboxEventHandlerMap;

  @Inject
  public PMSOutboxEventHandler(Map<String, OutboxEventHandler> outboxEventHandlerMap) {
    this.outboxEventHandlerMap = outboxEventHandlerMap;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      OutboxEventHandler handler = outboxEventHandlerMap.get(outboxEvent.getResource().getType());
      if (handler != null) {
        return handler.handle(outboxEvent);
      }
    } catch (Exception exception) {
      log.error(String.format("Unexpected error occurred during handling event of type %s", outboxEvent.getEventType()),
          exception);
    }
    return false;
  }
}