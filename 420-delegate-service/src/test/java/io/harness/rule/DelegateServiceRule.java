/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rule;

import static io.harness.lock.DistributedLockImplementation.NOOP;

import static org.mockito.Mockito.mock;

import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.factory.ClosingFactory;
import io.harness.factory.ClosingFactoryModule;
import io.harness.ff.FeatureFlagConfig;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.lock.DistributedLockImplementation;
import io.harness.metrics.modules.MetricsModule;
import io.harness.module.AgentMtlsModule;
import io.harness.module.DelegateAuthModule;
import io.harness.module.DelegateServiceModule;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaModule;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.observer.NoOpRemoteObserverInformerImpl;
import io.harness.observer.RemoteObserver;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.consumer.AbstractRemoteObserverModule;
import io.harness.outbox.api.OutboxDao;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.api.impl.OutboxDaoImpl;
import io.harness.outbox.api.impl.OutboxServiceImpl;
import io.harness.persistence.HPersistence;
import io.harness.redis.RedisConfig;
import io.harness.redis.intfc.DelegateRedissonCacheManager;
import io.harness.repositories.FilterRepository;
import io.harness.repositories.outbox.OutboxEventRepository;
import io.harness.serializer.DelegateServiceRegistrars;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.PersistenceRegistrars;
import io.harness.service.impl.DelegateCacheImpl;
import io.harness.service.intfc.DelegateCache;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.waiter.AbstractWaiterModule;
import io.harness.waiter.WaiterConfiguration;
import io.harness.waiter.WaiterConfiguration.PersistenceLayer;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.converters.TypeConverter;
import io.serializer.HObjectMapper;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RedissonClient;

@Slf4j
public class DelegateServiceRule implements MethodRule, InjectorRuleMixin, MongoRuleMixin {
  ClosingFactory closingFactory;

  public DelegateServiceRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) throws Exception {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());

    List<Module> modules = new ArrayList<>();
    modules.add(new ClosingFactoryModule(closingFactory));
    modules.add(KryoModule.getInstance());
    modules.add(new MetricsModule());

    modules.add(new ProviderModule() {
      @Override
      protected void configure() {
        bind(TimeLimiter.class).toInstance(HTimeLimiter.create());
      }

      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(DelegateServiceRegistrars.kryoRegistrars)
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
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(PersistenceRegistrars.morphiaConverters)
            .build();
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Named("lock")
      @Singleton
      RedisConfig redisLockConfig() {
        return RedisConfig.builder().build();
      }

      @Provides
      @Singleton
      DistributedLockImplementation distributedLockImplementation() {
        return NOOP;
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Named("delegate")
      @Singleton
      public RLocalCachedMap<String, Delegate> getDelegateCache(DelegateRedissonCacheManager cacheManager) {
        return mock(RLocalCachedMap.class);
      }

      @Provides
      @Named("delegate_group")
      @Singleton
      public RLocalCachedMap<String, DelegateGroup> getDelegateGroupCache(DelegateRedissonCacheManager cacheManager) {
        return mock(RLocalCachedMap.class);
      }

      @Provides
      @Named("delegates_from_group")
      @Singleton
      public RLocalCachedMap<String, List<Delegate>> getDelegatesFromGroupCache(
          DelegateRedissonCacheManager cacheManager) {
        return mock(RLocalCachedMap.class);
      }

      @Provides
      @Singleton
      @Named("enableRedisForDelegateService")
      boolean isEnableRedisForDelegateService() {
        return false;
      }

      @Provides
      @Singleton
      @Named("redissonClient")
      RedissonClient redissonClient() {
        return mock(RedissonClient.class);
      }
    });

    modules.add(new AbstractCfModule() {
      @Override
      public CfClientConfig cfClientConfig() {
        return CfClientConfig.builder().build();
      }

      @Override
      public CfMigrationConfig cfMigrationConfig() {
        return CfMigrationConfig.builder().build();
      }

      @Override
      public FeatureFlagConfig featureFlagConfig() {
        return FeatureFlagConfig.builder().build();
      }
    });

    modules.add(new AbstractWaiterModule() {
      @Override
      public WaiterConfiguration waiterConfiguration() {
        return WaiterConfiguration.builder().persistenceLayer(PersistenceLayer.MORPHIA).build();
      }
    });

    modules.add(new DelegateServiceModule());
    modules.add(new DelegateAuthModule());
    modules.add(TestMongoModule.getInstance());
    modules.add(MorphiaModule.getInstance());

    modules.add(new AgentMtlsModule());

    modules.add(mongoTypeModule(annotations));
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(HPersistence.class).to(MongoPersistence.class);
        bind(FilterRepository.class).toInstance(mock(FilterRepository.class));
        bind(OutboxDao.class).to(OutboxDaoImpl.class);
        bind(OutboxService.class).to(OutboxServiceImpl.class);
        bind(OutboxEventRepository.class).toInstance(mock(OutboxEventRepository.class));
        bind(DelegateCache.class).to(DelegateCacheImpl.class).in(Singleton.class);
      }
    });
    modules.add(new AbstractRemoteObserverModule() {
      @Override
      public boolean noOpProducer() {
        return true;
      }

      @Override
      public Set<RemoteObserver> observers() {
        return Collections.emptySet();
      }

      @Override
      public Class<? extends RemoteObserverInformer> getRemoteObserverImpl() {
        return NoOpRemoteObserverInformerImpl.class;
      }
    });
    return modules;
  }

  @Override
  public void initialize(Injector injector, List<Module> modules) {
    for (Module module : modules) {
      if (module instanceof ServersModule) {
        for (Closeable server : ((ServersModule) module).servers(injector)) {
          closingFactory.addServer(server);
        }
      }
    }
  }

  @Override
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object target) {
    return applyInjector(log, statement, frameworkMethod, target);
  }

  @Provides
  @Singleton
  OutboxService getOutboxService(OutboxEventRepository outboxEventRepository) {
    return new OutboxServiceImpl(new OutboxDaoImpl(outboxEventRepository), HObjectMapper.NG_DEFAULT_OBJECT_MAPPER);
  }
}
