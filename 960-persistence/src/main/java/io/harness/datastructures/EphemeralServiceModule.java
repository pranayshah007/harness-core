/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.datastructures;

import io.harness.govern.ProviderModule;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

@Slf4j
public class EphemeralServiceModule extends ProviderModule {
  private static volatile EphemeralServiceModule instance;

  public static EphemeralServiceModule getInstance() {
    if (instance == null) {
      instance = new EphemeralServiceModule();
    }
    return instance;
  }

  @Provides
  @Singleton
  EphemeralCacheService ephemeralServiceImpl(
      DistributedBackend distributedBackend, @Named("cacheRedissonClient") RedissonClient redissonClient) {
    switch (distributedBackend) {
      case NOOP:
        log.info("Initialize Noop Locker");
        return new NoopEphemeralCacheServiceImpl();
      case REDIS:
        log.info("Initialize Redis Locker");
        return new RedisEphemeralCacheService(redissonClient);
      default:
        throw new UnsupportedOperationException();
    }
  }
}
