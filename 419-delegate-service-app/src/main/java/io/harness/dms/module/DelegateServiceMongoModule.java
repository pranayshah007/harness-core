/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dms.module;

import static io.harness.lock.DistributedLockImplementation.MONGO;

import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.dms.configuration.DelegateServiceConfiguration;
import io.harness.govern.ProviderModule;
import io.harness.lock.DistributedLockImplementation;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.redis.RedisConfig;
import io.harness.serializer.DelegateServiceRegistrars;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.converters.TypeConverter;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DelegateServiceMongoModule extends ProviderModule {
  private final DelegateServiceConfiguration config;

  public DelegateServiceMongoModule(DelegateServiceConfiguration config) {
    this.config = config;
  }

  @Provides
  @Singleton
  @Named("morphiaClasses")
  Map<Class, String> morphiaCustomCollectionNames() {
    // this is needed because DelegateSyncTaskResponse and others need custom names.
    return ImmutableMap.<Class, String>builder()
        .put(DelegateSyncTaskResponse.class, "delegateSyncTaskResponses")
        .put(DelegateAsyncTaskResponse.class, "delegateAsyncTaskResponses")
        .put(DelegateTaskProgressResponse.class, "delegateTaskProgressResponses")
        .build();
  }

  @Provides
  @Singleton
  Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
    return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
        .addAll(DelegateServiceRegistrars.morphiaRegistrars)
        .build();
  }

  @Provides
  @Singleton
  Set<Class<? extends TypeConverter>> morphiaConverters() {
    return ImmutableSet.<Class<? extends TypeConverter>>builder().build();
  }

  @Provides
  @Singleton
  MongoConfig mongoConfig() {
    return config.getMongoConfig();
  }

  @Provides
  @Singleton
  @Named("dbAliases")
  public List<String> getDbAliases() {
    return config.getDbAliases();
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return config.getDistributedLockImplementation() == null ? MONGO : config.getDistributedLockImplementation();
  }

  @Provides
  @Named("lock")
  @Singleton
  RedisConfig redisLockConfig() {
    return config.getRedisLockConfig();
  }

  @Override
  protected void configure() {
    install(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
      }
    });
    bind(HPersistence.class).to(MongoPersistence.class);
  }
}
