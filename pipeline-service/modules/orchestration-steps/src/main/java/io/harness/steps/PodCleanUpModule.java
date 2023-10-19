/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.threading.ThreadPool;
import io.harness.threading.ThreadPoolConfig;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;

@OwnedBy(HarnessTeam.CDP)
public class PodCleanUpModule extends AbstractModule {
  private static PodCleanUpModule instance;
  private final MetricRegistry threadPoolMetricRegistry;
  private final ThreadPoolConfig podCleanUpThreadPoolConfig;

  public static PodCleanUpModule getInstance(
      ThreadPoolConfig podCleanUpThreadPoolConfig, MetricRegistry threadPoolMetricRegistry) {
    if (instance == null) {
      instance = new PodCleanUpModule(podCleanUpThreadPoolConfig, threadPoolMetricRegistry);
    }
    return instance;
  }
  PodCleanUpModule(ThreadPoolConfig podCleanUpThreadPoolConfig, MetricRegistry threadPoolMetricRegistry) {
    this.podCleanUpThreadPoolConfig = podCleanUpThreadPoolConfig;
    this.threadPoolMetricRegistry = threadPoolMetricRegistry;
  }

  @Provides
  @Singleton
  @Named("PodCleanUpExecutorService")
  public ExecutorService podCleanUpExecutorService() {
    return ThreadPool.getInstrumentedExecutorService(
        podCleanUpThreadPoolConfig, "PodCleanUpExecutorService", threadPoolMetricRegistry);
  }
}
