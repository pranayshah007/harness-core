/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.outbox;

import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.cronutils.utils.Preconditions;
import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CVServiceOutboxEventHandler implements OutboxEventHandler {
  private final Map<String, OutboxEventHandler> outboxEventHandlerMap;

  @Inject
  public CVServiceOutboxEventHandler(Map<String, OutboxEventHandler> outboxEventHandlerMap) {
    this.outboxEventHandlerMap = outboxEventHandlerMap;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    Preconditions.checkNotNull(outboxEvent);
    OutboxEventHandler handler = outboxEventHandlerMap.get(outboxEvent.getResource().getType());
    return handler.handle(outboxEvent);
  }
}
