/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.visualisation.log.OrchestrationLogEvent;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.Serializable;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.EventType;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class OrchestrationLogCacheListener
    implements CacheEntryExpiredListener<String, Long>, CacheEntryEventFilter<String, Long>, Serializable {
  @Inject @Named(EventsFrameworkConstants.ORCHESTRATION_LOG) private Producer producer;

  private static final long serialVersionUID = 1L;

  public void onExpired(Iterable<CacheEntryEvent<? extends String, ? extends Long>> events)
      throws CacheEntryListenerException {
    for (CacheEntryEvent<? extends String, ? extends Long> event : events) {
      String planExecutionId = event.getKey();
      if (event.getValue() == 0L) {
        continue;
      }
      try (AutoLogContext autoLogContext = new AutoLogContext(
               ImmutableMap.of("planExecutionId", planExecutionId), AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
        OrchestrationLogEvent orchestrationLogEvent =
            OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId).build();
        log.info("Sending batch event for orchestrationEventLog");
        producer.send(Message.newBuilder()
                          .putAllMetadata(ImmutableMap.of("planExecutionId", event.getKey()))
                          .setData(orchestrationLogEvent.toByteString())
                          .build());
      }
    }
  }

  @Override
  public boolean evaluate(CacheEntryEvent<? extends String, ? extends Long> cacheEntryEvent)
      throws CacheEntryListenerException {
    return cacheEntryEvent.getEventType() == EventType.EXPIRED;
  }
}
