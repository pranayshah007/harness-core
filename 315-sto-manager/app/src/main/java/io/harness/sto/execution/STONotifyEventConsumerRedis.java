/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.sto.execution;

import static io.harness.eventsframework.EventsFrameworkConstants.STO_ORCHESTRATION_NOTIFY_EVENT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.pms.events.base.PmsAbstractRedisConsumer;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import javax.cache.Cache;

@Singleton
@OwnedBy(HarnessTeam.STO)
public final class STONotifyEventConsumerRedis extends PmsAbstractRedisConsumer<STONotifyEventMessageListener> {
  public static final String STO_EVENTS_CACHE = "stoEventsCache";

  @Inject
  public STONotifyEventConsumerRedis(@Named(STO_ORCHESTRATION_NOTIFY_EVENT) Consumer redisConsumer,
      STONotifyEventMessageListener messageListener, @Named(STO_EVENTS_CACHE) Cache<String, Integer> eventsCache,
      QueueController queueController) {
    super(redisConsumer, messageListener, eventsCache, queueController);
  }
}
