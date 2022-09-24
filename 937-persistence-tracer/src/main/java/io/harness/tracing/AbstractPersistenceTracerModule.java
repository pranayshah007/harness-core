/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.tracing;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.HarnessCacheManager;
import io.harness.mongo.tracing.TracerConstants;
import io.harness.redis.RedisConfig;
import io.harness.version.VersionInfoManager;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@OwnedBy(HarnessTeam.PIPELINE)
public abstract class AbstractPersistenceTracerModule extends AbstractModule {
  @Override
  protected void configure() {
    install(PersistenceTracerModule.getInstance());
    requireBinding(HarnessCacheManager.class);
    requireBinding(VersionInfoManager.class);
  }

  @Provides
  @Singleton
  RedisConfig redisConfig() {
    return redisConfigProvider();
  }

  @Provides
  @Named(TracerConstants.SERVICE_ID)
  public String serviceId() {
    return serviceIdProvider();
  }

  protected abstract RedisConfig redisConfigProvider();

  protected abstract String serviceIdProvider();
}
