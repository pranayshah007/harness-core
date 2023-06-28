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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;

@OwnedBy(HarnessTeam.CDP)
public class PodCleanUpModule extends AbstractModule {
  private static PodCleanUpModule instance;

  private final ThreadPoolConfig podCleanUpThreadPoolConfig;

  public static PodCleanUpModule getInstance(ThreadPoolConfig podCleanUpThreadPoolConfig) {
    if (instance == null) {
      instance = new PodCleanUpModule(podCleanUpThreadPoolConfig);
    }
    return instance;
  }

  PodCleanUpModule(ThreadPoolConfig podCleanUpThreadPoolConfig) {
    this.podCleanUpThreadPoolConfig = podCleanUpThreadPoolConfig;
  }

  @Provides
  @Singleton
  @Named("PodCleanUpExecutorService")
  public ExecutorService podCleanUpExecutorService() {
    return ThreadPool.create(podCleanUpThreadPoolConfig.getCorePoolSize(), podCleanUpThreadPoolConfig.getMaxPoolSize(),
        podCleanUpThreadPoolConfig.getIdleTime(), podCleanUpThreadPoolConfig.getTimeUnit(),
        new ThreadFactoryBuilder().setNameFormat("PodCleanUpExecutorService-%d").build());
  }
}
