/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class NextGenOutboxEventHandler implements OutboxEventHandler {
  private final Map<String, OutboxEventHandler> outboxEventHandlerMap;

  @Inject
  public NextGenOutboxEventHandler(Map<String, OutboxEventHandler> outboxEventHandlerMap) {
    this.outboxEventHandlerMap = outboxEventHandlerMap;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      OutboxEventHandler handler = outboxEventHandlerMap.get(outboxEvent.getResource().getType());
      if (handler != null) {
        return handler.handle(outboxEvent);
      }
      log.error("No outbox event handler found for eventType: '{}' and eventId: '{}'", outboxEvent.getEventType(),
          outboxEvent.getId());
    } catch (Exception exception) {
      log.error(String.format("Unexpected error occurred during handling event of type %s", outboxEvent.getEventType()),
          exception);
    }

    return false;
  }
}
