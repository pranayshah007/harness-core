/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cache;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.cache.CacheBackend.REDIS;

import static javax.cache.Caching.getCachingProvider;

import io.harness.annotations.dev.OwnedBy;
import io.harness.govern.ProviderMethodInterceptor;
import io.harness.govern.ServersModule;
import io.harness.redis.RedissonKryoCodec;

import com.google.common.io.Files;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.annotation.CacheKeyGenerator;
import javax.cache.annotation.CachePut;
import javax.cache.annotation.CacheRemove;
import javax.cache.annotation.CacheRemoveAll;
import javax.cache.annotation.CacheResolverFactory;
import javax.cache.annotation.CacheResult;
import javax.cache.spi.CachingProvider;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.jsr107.ri.annotations.CacheContextSource;
import org.jsr107.ri.annotations.DefaultCacheKeyGenerator;
import org.jsr107.ri.annotations.guice.CacheLookupUtil;
import org.jsr107.ri.annotations.guice.CachePutInterceptor;
import org.jsr107.ri.annotations.guice.CacheRemoveAllInterceptor;
import org.jsr107.ri.annotations.guice.CacheRemoveEntryInterceptor;
import org.jsr107.ri.annotations.guice.CacheResultInterceptor;
import org.redisson.config.Config;

/**
 * Created by peeyushaggarwal on 1/11/17.
 * <p>
 * Copyright 2011-2013 Terracotta, Inc.
 * Copyright 2011-2013 Oracle America Incorporated
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p>
 * Copyright 2011-2013 Terracotta, Inc.
 * Copyright 2011-2013 Oracle America Incorporated
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle America Incorporated
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 * Standard cache module for binding all cache interceptors to their respective annotations. This module needs to be
 * part of the Guice injector instantiation to activate intercepting of the cache annotations. Every interceptor is
 * bound twice due to the fact that the annotations defining the joinpoints have retention type Method and Type.
 *
 * @author Michael Stachel
 * @version $Revision$
 */
@OwnedBy(PL)
@Slf4j
public class CacheModule extends AbstractModule implements ServersModule {
  private static final String CACHING_PROVIDER_CLASSPATH = "javax.cache.spi.CachingProvider";
  private CacheManager cacheManager;
  private Optional<CacheManager> enterpriseRedisCacheManagerOptional;
  private CacheConfig cacheConfig;

  public CacheModule(@NonNull CacheConfig cacheConfig) {
    this.cacheConfig = cacheConfig;
  }

  private CacheManager getCacheManagerFromYAML(String filePath) throws IOException {
    System.setProperty(CACHING_PROVIDER_CLASSPATH, "org.redisson.jcache.JCachingProvider");
    CachingProvider provider = getCachingProvider();
    URI uri = provider.getDefaultURI();
    File file = new File(filePath);
    if (file.exists()) {
      uri = file.toURI();
      Config config = Config.fromYAML(uri.toURL());
      config.setCodec(new RedissonKryoCodec());
      Files.write(config.toYAML().getBytes(StandardCharsets.UTF_8), file);
      log.info("Found the redisson config in the working directory {}", uri);
    }
    return provider.getCacheManager(uri, provider.getDefaultClassLoader(), new Properties());
  }

  @Provides
  @Named("Redis")
  @Singleton
  CacheManager getRedissonCacheManager() throws IOException {
    return getCacheManagerFromYAML("redisson-jcache.yaml");
  }

  @Provides
  @Named("EnterpriseRedis")
  @Singleton
  public Optional<CacheManager> getEnterpriseRedissonCacheManagerOptional() throws IOException {
    if (cacheConfig.isEnterpriseCacheEnabled()) {
      return Optional.of(getCacheManagerFromYAML("enterprise-redisson-jcache.yaml"));
    }

    return Optional.empty();
  }

  @Provides
  @Named("Caffeine")
  @Singleton
  CacheManager getCaffeineCacheManager() {
    System.setProperty(CACHING_PROVIDER_CLASSPATH, "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider");
    Properties properties = new Properties();
    CachingProvider provider = getCachingProvider();
    return provider.getCacheManager(provider.getDefaultURI(), provider.getDefaultClassLoader(), properties);
  }

  @Provides
  @Singleton
  public HarnessCacheManager getHarnessCacheManager(@Named("Redis") Provider<CacheManager> redisProvider,
      @Named("EnterpriseRedis") Provider<Optional<CacheManager>> enterpriseRedisProvider,
      @Named("Caffeine") Provider<CacheManager> caffeineProvider) {
    CacheBackend cacheBackend = cacheConfig.getCacheBackend();
    switch (cacheBackend) {
      case NOOP:
      case HAZELCAST:
        return new NoOpHarnessCacheManager();
      case REDIS:
        this.cacheManager = redisProvider.get();
        this.enterpriseRedisCacheManagerOptional = enterpriseRedisProvider.get();
        break;
      case CAFFEINE:
        this.cacheManager = caffeineProvider.get();
        break;
      default:
        throw new UnsupportedOperationException();
    }
    return new HarnessCacheManagerImpl(cacheManager, enterpriseRedisCacheManagerOptional, cacheConfig);
  }

  public static <T, R> Supplier<R> bind(Function<T, R> fn, T val) {
    return () -> fn.apply(val);
  }

  @Override
  protected void configure() {
    if (cacheConfig.getCacheBackend() == REDIS) {
      bind(RedissonKryoCodec.class).toInstance(new RedissonKryoCodec());
    }
    MapBinder.newMapBinder(binder(), TypeLiteral.get(String.class), new TypeLiteral<Cache<?, ?>>() {});

    bind(CacheResolverFactory.class).to(GuiceCacheResolverFactory.class);
    bind(CacheKeyGenerator.class).to(DefaultCacheKeyGenerator.class);
    bind(new TypeLiteral<CacheContextSource<MethodInvocation>>() {}).to(CacheLookupUtil.class);

    ProviderMethodInterceptor cachePutInterceptor =
        new ProviderMethodInterceptor(getProvider(CachePutInterceptor.class));
    bindInterceptor(Matchers.annotatedWith(CachePut.class), Matchers.any(), cachePutInterceptor);
    bindInterceptor(Matchers.any(), Matchers.annotatedWith(CachePut.class), cachePutInterceptor);

    ProviderMethodInterceptor cacheResultInterceptor =
        new ProviderMethodInterceptor(getProvider(CacheResultInterceptor.class));
    bindInterceptor(Matchers.annotatedWith(CacheResult.class), Matchers.any(), cacheResultInterceptor);
    bindInterceptor(Matchers.any(), Matchers.annotatedWith(CacheResult.class), cacheResultInterceptor);

    ProviderMethodInterceptor cacheRemoveEntryInterceptor =
        new ProviderMethodInterceptor(getProvider(CacheRemoveEntryInterceptor.class));
    bindInterceptor(Matchers.annotatedWith(CacheRemove.class), Matchers.any(), cacheRemoveEntryInterceptor);
    bindInterceptor(Matchers.any(), Matchers.annotatedWith(CacheRemove.class), cacheRemoveEntryInterceptor);

    ProviderMethodInterceptor cacheRemoveAllInterceptor =
        new ProviderMethodInterceptor(getProvider(CacheRemoveAllInterceptor.class));
    bindInterceptor(Matchers.annotatedWith(CacheRemoveAll.class), Matchers.any(), cacheRemoveAllInterceptor);
    bindInterceptor(Matchers.any(), Matchers.annotatedWith(CacheRemoveAll.class), cacheRemoveAllInterceptor);
  }

  @Override
  public List<Closeable> servers(Injector injector) {
    return Collections.singletonList(() -> {
      if (cacheManager != null) {
        cacheManager.close();
      }
    });
  }

  @Provides
  @Singleton
  public CachePutInterceptor getCachePutInterceptor(Injector injector) {
    CachePutInterceptor cachePutInterceptor = new CachePutInterceptor();
    injector.injectMembers(cachePutInterceptor);
    return cachePutInterceptor;
  }

  @Provides
  @Singleton
  public CacheResultInterceptor getCacheResultInterceptor(Injector injector) {
    CacheResultInterceptor cacheResultInterceptor = new CacheResultInterceptor();
    injector.injectMembers(cacheResultInterceptor);
    return cacheResultInterceptor;
  }

  @Provides
  @Singleton
  public CacheRemoveEntryInterceptor getCacheRemoveEntryInterceptor(Injector injector) {
    CacheRemoveEntryInterceptor cacheRemoveEntryInterceptor = new CacheRemoveEntryInterceptor();
    injector.injectMembers(cacheRemoveEntryInterceptor);
    return cacheRemoveEntryInterceptor;
  }

  @Provides
  @Singleton
  public CacheRemoveAllInterceptor getCacheRemoveAllInterceptor(Injector injector) {
    CacheRemoveAllInterceptor cacheRemoveAllInterceptor = new CacheRemoveAllInterceptor();
    injector.injectMembers(cacheRemoveAllInterceptor);
    return cacheRemoveAllInterceptor;
  }
}
