/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.config;

import io.harness.service.impl.DelegateAsyncServiceImpl;
import io.harness.service.impl.DelegateProgressServiceImpl;
import io.harness.service.impl.DelegateSyncServiceImpl;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.guice.annotation.EnableGuiceModules;

@Configuration
@EnableGuiceModules
public class AuditEventBatchApplicationConfiguration {
  @Autowired Injector injector;

  public static final String TASK_POLL_EXECUTOR = "taskPollExecutor";

  @EventListener(ContextRefreshedEvent.class)
  public void registerScheduleJobs(ContextRefreshedEvent event) {
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named(TASK_POLL_EXECUTOR)))
        .scheduleWithFixedDelay(injector.getInstance(DelegateSyncServiceImpl.class), 0L, 2L, TimeUnit.SECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named(TASK_POLL_EXECUTOR)))
        .scheduleWithFixedDelay(injector.getInstance(DelegateAsyncServiceImpl.class), 0L, 5L, TimeUnit.SECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named(TASK_POLL_EXECUTOR)))
        .scheduleWithFixedDelay(injector.getInstance(DelegateProgressServiceImpl.class), 0L, 5L, TimeUnit.SECONDS);
  }
}
