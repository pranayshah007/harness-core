/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;
import static io.harness.cache.CacheBackend.CAFFEINE;
import static io.harness.cache.CacheBackend.NOOP;

import static org.mockito.Mockito.mock;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cache.CacheConfig;
import io.harness.cache.CacheConfig.CacheConfigBuilder;
import io.harness.cache.CacheModule;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.factory.ClosingFactory;
import io.harness.factory.ClosingFactoryModule;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.pms.sdk.core.PmsSdkCoreConfig;
import io.harness.pms.sdk.core.PmsSdkCoreModule;
import io.harness.pms.sdk.core.SdkDeployMode;
import io.harness.queue.QueueController;
import io.harness.redis.RedisConfig;
import io.harness.rule.Cache;
import io.harness.rule.InjectorRuleMixin;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.NGCommonModuleRegistrars;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@Slf4j
public class NGCommonUtilitiesRule implements MethodRule, InjectorRuleMixin {
  ClosingFactory closingFactory;

  public NGCommonUtilitiesRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) throws Exception {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());
    EventsFrameworkConfiguration eventsFrameworkConfiguration =
        EventsFrameworkConfiguration.builder()
            .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
            .build();
    List<Module> modules = new ArrayList<>();
    CacheConfigBuilder cacheConfigBuilder =
        CacheConfig.builder().disabledCaches(new HashSet<>()).cacheNamespace("harness-cache");
    if (annotations.stream().anyMatch(annotation -> annotation instanceof Cache)) {
      cacheConfigBuilder.cacheBackend(CAFFEINE);
    } else {
      cacheConfigBuilder.cacheBackend(NOOP);
    }
    CacheModule cacheModule = new CacheModule(cacheConfigBuilder.build());
    modules.add(cacheModule);
    modules.add(new ClosingFactoryModule(closingFactory));
    modules.add(PmsSdkCoreModule.getInstance(PmsSdkCoreConfig.builder()
                                                 .serviceName(NGCommonUtilitiesTestBase.PMS_SERVICE_NAME)
                                                 .sdkDeployMode(SdkDeployMode.LOCAL)
                                                 .eventsFrameworkConfiguration(eventsFrameworkConfiguration)
                                                 .build()));
    modules.add(PmsCommonsModule.getInstance());
    modules.add(KryoModule.getInstance());

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(NGCommonModuleRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(NGCommonModuleRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder().build();
      }
    });

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(QueueController.class).toInstance(new QueueController() {
          @Override
          public boolean isPrimary() {
            return true;
          }

          @Override
          public boolean isNotPrimary() {
            return false;
          }
        });
        bind(Producer.class)
            .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_ACTIVITY))
            .toInstance(mock(NoOpProducer.class));
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
}
