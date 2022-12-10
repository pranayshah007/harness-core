/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules.platform;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

import io.harness.threading.ThreadPool;

import software.wings.misc.MemoryHelper;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DelegateExecutorsModule extends AbstractModule {
  private final boolean dynamicHandlingOfRequestEnabled;

  /*
   * Creates and return ScheduledExecutorService object, which can be used for health monitoring purpose.
   * This threadpool currently being used for various below operations:
   *  1) Sending heartbeat to manager and watcher.
   *  2) Receiving heartbeat from manager.
   *  3) Sending KeepAlive packet to manager.
   *  4) Perform self upgrade check.
   *  5) Perform watcher upgrade check.
   *  6) Track changes in delegate profile.
   */
  @Provides
  @Singleton
  @Named("healthMonitorExecutor")
  public ScheduledExecutorService healthMonitorExecutor() {
    return new ScheduledThreadPoolExecutor(
        20, new ThreadFactoryBuilder().setNameFormat("healthMonitor-%d").setPriority(Thread.MAX_PRIORITY).build());
  }

  @Provides
  @Singleton
  @Named("taskPollExecutor")
  public ScheduledExecutorService taskPollExecutor() {
    return new ScheduledThreadPoolExecutor(
        4, new ThreadFactoryBuilder().setNameFormat("task-poll-%d").setPriority(Thread.MAX_PRIORITY).build());
  }

  @Provides
  @Singleton
  @Named("taskExecutor")
  public ThreadPoolExecutor taskExecutor() {
    int maxPoolSize = Integer.MAX_VALUE;
    long delegateXmx = 0;
    try {
      delegateXmx = MemoryHelper.getProcessMaxMemoryMB();
      if (!dynamicHandlingOfRequestEnabled) {
        // Set max threads to 400, if dynamic handling is disabled.
        maxPoolSize = 400;
      }
    } catch (Exception ex) {
      // We failed to fetch memory bean, setting number of threads as 400.
      maxPoolSize = 400;
    }
    log.info(
        "Starting Delegate process with {} MB of Xmx and {} number of execution threads", delegateXmx, maxPoolSize);
    return ThreadPool.create(10, maxPoolSize, 1, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("task-exec-%d").setPriority(Thread.MIN_PRIORITY).build());
  }

  // FixMe: Not part of core - move to separate module
  @Provides
  @Singleton
  @Named("timeoutExecutor")
  public ThreadPoolExecutor timeoutExecutor() {
    return ThreadPool.create(10, 40, 7, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("timeout-%d").setPriority(Thread.NORM_PRIORITY).build());
  }

  @Provides
  @Singleton
  @Named("delegateAgentMetricsExecutor")
  public ScheduledExecutorService delegateAgentMetricsExecutor() {
    return newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                                                .setNameFormat("delegate-agent-metrics-executor-%d")
                                                .setPriority(Thread.NORM_PRIORITY)
                                                .build());
  }
}
