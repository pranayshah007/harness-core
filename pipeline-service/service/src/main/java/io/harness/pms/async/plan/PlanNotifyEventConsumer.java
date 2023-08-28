/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.async.plan;

import static io.harness.eventsframework.EventsFrameworkConstants.PLAN_NOTIFY_EVENT_TOPIC;
import static io.harness.pms.sdk.PmsSdkModuleUtils.CORE_EXECUTOR_NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.pms.events.base.PmsAbstractRedisConsumer;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class PlanNotifyEventConsumer extends PmsAbstractRedisConsumer<PlanNotifyMessageListener> {
  public static final String PMS_PLAN_CREATION = "pms_plan_creation";

  @Inject
  public PlanNotifyEventConsumer(@Named(PLAN_NOTIFY_EVENT_TOPIC) Consumer redisConsumer,
      PlanNotifyMessageListener messageListener, @Named("sdkEventsCache") Cache<String, Integer> eventsCache,
      QueueController queueController, @Named(CORE_EXECUTOR_NAME) ExecutorService executorService) {
    super(redisConsumer, messageListener, eventsCache, queueController, executorService);
  }
}
