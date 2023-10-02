/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.execution.events.interrupts;

import static io.harness.pms.sdk.PmsSdkModuleUtils.CORE_EXECUTOR_NAME;
import static io.harness.pms.sdk.execution.events.PmsSdkEventFrameworkConstants.SDK_INTERRUPT_CONSUMER;

import io.harness.eventsframework.api.Consumer;
import io.harness.pms.events.base.PmsAbstractRedisConsumer;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import javax.cache.Cache;

public class InterruptEventRedisConsumerV2 extends PmsAbstractRedisConsumer<InterruptEventMessageListener> {
  @Inject
  public InterruptEventRedisConsumerV2(@Named(SDK_INTERRUPT_CONSUMER) Consumer redisConsumer,
      InterruptEventMessageListener interruptEventMessageListener,
      @Named("sdkEventsCache") Cache<String, Integer> eventsCache, QueueController queueController,
      @Named(CORE_EXECUTOR_NAME) ExecutorService executorService) {
    super(redisConsumer, interruptEventMessageListener, eventsCache, queueController, executorService);
  }
}
