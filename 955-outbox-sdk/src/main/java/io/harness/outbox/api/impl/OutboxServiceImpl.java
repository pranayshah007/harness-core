/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.outbox.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.outbox.OutboxSDKConstants.DEFAULT_OUTBOX_EVENT_FILTER;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.exception.UnexpectedException;
import io.harness.manage.GlobalContextManager;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxDao;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.filter.OutboxEventFilter;

import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.List;
import javax.annotation.Nullable;

@OwnedBy(PL)
public class OutboxServiceImpl implements OutboxService {
  private final OutboxDao outboxDao;
  private final ObjectMapper objectMapper;

  @Inject
  public OutboxServiceImpl(OutboxDao outboxDao, @Nullable ObjectMapper objectMapper) {
    this.outboxDao = outboxDao;
    this.objectMapper = objectMapper == null ? NG_DEFAULT_OBJECT_MAPPER : objectMapper;
  }

  @Override
  public OutboxEvent save(Event event) {
    String eventData;
    try {
      eventData = objectMapper.writerWithView(JsonViews.Internal.class).writeValueAsString(event);
    } catch (JsonProcessingException exception) {
      throw new UnexpectedException(
          "JsonProcessingException occurred while serializing eventData in the outbox.", exception);
    }
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .resourceScope(event.getResourceScope())
                                  .resource(event.getResource())
                                  .eventData(eventData)
                                  .eventType(event.getEventType())
                                  .globalContext(GlobalContextManager.obtainGlobalContext())
                                  .build();
    return outboxDao.save(outboxEvent);
  }

  @Override
  public OutboxEvent update(OutboxEvent outboxEvent) {
    return outboxDao.save(outboxEvent);
  }

  @Override
  public List<OutboxEvent> list(OutboxEventFilter outboxEventFilter) {
    if (outboxEventFilter == null) {
      outboxEventFilter = DEFAULT_OUTBOX_EVENT_FILTER;
    }
    return outboxDao.list(outboxEventFilter);
  }

  @Override
  public boolean delete(String outboxEventId) {
    outboxDao.delete(outboxEventId);
    return true;
  }
}
