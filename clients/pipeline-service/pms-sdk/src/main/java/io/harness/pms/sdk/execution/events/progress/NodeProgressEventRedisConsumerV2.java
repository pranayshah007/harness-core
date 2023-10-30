/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.execution.events.progress;
import static io.harness.pms.sdk.PmsSdkModuleUtils.ORCHESTRATION_EVENT_EXECUTOR_NAME;
import static io.harness.pms.sdk.execution.events.PmsSdkEventFrameworkConstants.SDK_PROGRESS_CONSUMER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.api.Consumer;
import io.harness.pms.events.base.PmsAbstractRedisConsumer;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import javax.cache.Cache;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class NodeProgressEventRedisConsumerV2 extends PmsAbstractRedisConsumer<ProgressEventMessageListener> {
  @Inject
  public NodeProgressEventRedisConsumerV2(@Named(SDK_PROGRESS_CONSUMER) Consumer redisConsumer,
      ProgressEventMessageListener messageListener, @Named("sdkEventsCache") Cache<String, Integer> eventsCache,
      QueueController queueController, @Named(ORCHESTRATION_EVENT_EXECUTOR_NAME) ExecutorService executorService) {
    super(redisConsumer, messageListener, eventsCache, queueController, executorService);
  }
}
