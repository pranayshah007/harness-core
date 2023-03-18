/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.queue.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.CG_NOTIFY_EVENT;

import static software.wings.app.ManagerCacheRegistrar.WAIT_ENGINE_EVENT_CACHE;

import io.harness.eventsframework.api.Consumer;
import io.harness.ng.core.event.MessageListener;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import javax.cache.Cache;

public class NotifyEventConsumerCg extends AbstractBaseConsumerCg {
  @Inject
  public NotifyEventConsumerCg(@Named(CG_NOTIFY_EVENT) Consumer redisConsumer,
      @Named(CG_NOTIFY_EVENT) MessageListener messageListener, QueueController queueController,
      @Named(WAIT_ENGINE_EVENT_CACHE) Cache<String, Integer> cache) {
    super(redisConsumer, messageListener, queueController, cache);
  }
}
