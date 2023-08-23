/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.waiter;

import static io.harness.eventsframework.EventsFrameworkConstants.PMS_ORCHESTRATION_NOTIFY_EVENT;
import static io.harness.pms.sdk.PmsSdkModuleUtils.CORE_EXECUTOR_NAME;

import io.harness.eventsframework.api.Consumer;
import io.harness.pms.events.base.PmsAbstractRedisConsumer;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import javax.cache.Cache;

@Singleton
public class PmsNotifyEventConsumerRedis extends PmsAbstractRedisConsumer<PmsNotifyEventMessageListener> {
  @Inject
  public PmsNotifyEventConsumerRedis(@Named(PMS_ORCHESTRATION_NOTIFY_EVENT) Consumer redisConsumer,
      PmsNotifyEventMessageListener messageListener, @Named("pmsEventsCache") Cache<String, Integer> eventsCache,
      QueueController queueController, @Named(CORE_EXECUTOR_NAME) ExecutorService executorService) {
    super(redisConsumer, messageListener, eventsCache, queueController, executorService);
  }
}
