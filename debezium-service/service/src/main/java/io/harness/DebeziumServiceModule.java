/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.concurrent.HTimeLimiter;
import io.harness.debezium.DebeziumEngineModule;
import io.harness.debezium.DebeziumEngineModuleConfig;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.metrics.modules.MetricsModule;
import io.harness.mongo.MongoPersistence;
import io.harness.mongo.index.migrator.Migrator;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.redis.RedisConfig;
import io.harness.threading.ThreadPool;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.mongodb.MongoClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.mongodb.morphia.AdvancedDatastore;

public class DebeziumServiceModule extends AbstractModule {
  private static DebeziumServiceModule instance;

  private final DebeziumServiceModuleConfig moduleConfig;

  private DebeziumServiceModule(DebeziumServiceModuleConfig moduleConfig) {
    this.moduleConfig = moduleConfig;
  }

  public static DebeziumServiceModule getInstance(DebeziumServiceModuleConfig moduleConfig) {
    if (instance == null) {
      instance = new DebeziumServiceModule(moduleConfig);
    }
    return instance;
  }

  @Override
  public void configure() {
    install(DebeziumEngineModule.getInstance(
        DebeziumEngineModuleConfig.builder()
            .eventsFrameworkConfiguration(moduleConfig.getEventsFrameworkConfiguration())
            .build()));
    install(PersistentLockModule.getInstance());
    install(new MetricsModule());
    bind(HPersistence.class).to(MongoPersistence.class);
    bind(ExecutorService.class)
        .toInstance(ThreadPool.create(1, 10, 120, TimeUnit.SECONDS,
            new ThreadFactoryBuilder().setNameFormat("DebeziumServiceExecutor-%d").build()));
    MapBinder.newMapBinder(binder(), String.class, Migrator.class);
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return moduleConfig.getLockImplementation();
  }

  @Provides
  @Named("lock")
  @Singleton
  RedisConfig redisConfig() {
    return moduleConfig.getRedisLockConfig();
  }

  // Faking all the mongo related stuff so that we do not need to spawn a database
  @Provides
  @Singleton
  public TimeLimiter timeLimiter(ExecutorService executorService) {
    return HTimeLimiter.create(executorService);
  }

  @Provides
  @Named("locksDatabase")
  @Singleton
  public String databaseNameProvider() {
    return null;
  }

  // Just a fake client
  @Provides
  @Named("locksMongoClient")
  @Singleton
  public MongoClient locksMongoClient() {
    return null;
  }

  @Provides
  @Named("primaryDatastore")
  @Singleton
  public AdvancedDatastore datastore() {
    return null;
  }

  @Provides
  @Singleton
  public UserProvider userProvider() {
    return new NoopUserProvider();
  }
}
