/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executor.common;

import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;
import io.harness.time.TimeModule;
import io.harness.version.VersionModule;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class CommonModule extends AbstractModule {
  // FIXME: check if we can remove it
  @Provides
  @Singleton
  @Named("verificationDataCollectorExecutor")
  public ExecutorService verificationDataCollectorExecutor() {
    return ThreadPool.create(4, 20, 5, TimeUnit.SECONDS,
        new ThreadFactoryBuilder()
            .setNameFormat("verificationDataCollector-%d")
            .setPriority(Thread.MIN_PRIORITY)
            .build());
  }

  @Override
  public void configure() {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());
    install(VersionModule.getInstance());
    install(TimeModule.getInstance());
  }
}
