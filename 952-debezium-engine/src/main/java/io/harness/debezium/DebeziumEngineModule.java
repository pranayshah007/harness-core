/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.impl.redis.RedisProducerFactory;
import io.harness.lock.PersistentLocker;
import io.harness.threading.ThreadPool;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class DebeziumEngineModule extends AbstractModule {
  private static DebeziumEngineModule instance;

  private final DebeziumEngineModuleConfig config;

  private DebeziumEngineModule(DebeziumEngineModuleConfig config) {
    this.config = config;
  }

  public static DebeziumEngineModule getInstance(DebeziumEngineModuleConfig config) {
    if (instance == null) {
      instance = new DebeziumEngineModule(config);
    }
    return instance;
  }

  @Override
  public void configure() {
    requireBinding(PersistentLocker.class);
    requireBinding(RedisProducerFactory.class);
  }

  @Provides
  @Singleton
  @Named("DebeziumExecutorService")
  public ExecutorService executorService() {
    return ThreadPool.create(1, 200, 120, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("debezium-controller-thread-%d").build());
  }

  @Provides
  @Singleton
  public EventsFrameworkConfiguration eventsFrameworkConfiguration() {
    return config.getEventsFrameworkConfiguration();
  }
}
