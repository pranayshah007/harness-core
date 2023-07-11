/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.cache.CacheBackend.CAFFEINE;
import static io.harness.cache.CacheBackend.NOOP;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.mockito.Mockito.mock;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.PrimaryVersionManagerModule;
import io.harness.cache.CacheConfig;
import io.harness.cache.CacheConfig.CacheConfigBuilder;
import io.harness.cache.CacheModule;
import io.harness.callback.DelegateCallbackToken;
import io.harness.customDeployment.remote.CustomDeploymentResourceClient;
import io.harness.delegate.DelegateServiceGrpc;
import io.harness.factory.ClosingFactory;
import io.harness.filter.FiltersModule;
import io.harness.gitsync.HarnessToGitPushInfoServiceGrpc;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.persistance.NoOpGitSyncSdkServiceImpl;
import io.harness.gitsync.persistance.testing.GitSyncablePersistenceTestModule;
import io.harness.gitsync.persistance.testing.NoOpGitAwarePersistenceImpl;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.manage.ManagedExecutorService;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.oas.OASModule;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.api.impl.OutboxDaoImpl;
import io.harness.outbox.api.impl.OutboxServiceImpl;
import io.harness.persistence.HPersistence;
import io.harness.repositories.outbox.OutboxEventRepository;
import io.harness.rule.Cache;
import io.harness.rule.InjectorRuleMixin;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.TemplateServiceModuleRegistrars;
import io.harness.serializer.jackson.TemplateServiceJacksonModule;
import io.harness.service.intfc.DelegateAsyncService;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.springdata.HTransactionTemplate;
import io.harness.telemetry.TelemetryReporter;
import io.harness.template.services.NoOpTemplateGitXServiceImpl;
import io.harness.template.services.TemplateGitXService;
import io.harness.template.utils.NGTemplateFeatureFlagHelperService;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.time.TimeModule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import io.dropwizard.jackson.Jackson;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.serializer.HObjectMapper;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.mockito.Mockito;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@OwnedBy(CDC)
public class TemplateServiceTestRule implements InjectorRuleMixin, MethodRule, MongoRuleMixin {
  ClosingFactory closingFactory;

  public TemplateServiceTestRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) throws Exception {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());

    List<Module> modules = new ArrayList<>();
    modules.add(KryoModule.getInstance());
    modules.add(new OASModule() {
      @Override
      public Collection<Class<?>> getResourceClasses() {
        return TemplateServiceConfiguration.getResourceClasses();
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(TemplateServiceModuleRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(TemplateServiceModuleRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(TemplateServiceModuleRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      TransactionTemplate getTransactionTemplate(MongoTransactionManager mongoTransactionManager) {
        return new HTransactionTemplate(mongoTransactionManager, false);
      }

      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return MongoConfig.builder().build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
            .addAll(TemplateServiceModuleRegistrars.springConverters)
            .build();
      }

      @Provides
      @Singleton
      OutboxService getOutboxService(OutboxEventRepository outboxEventRepository) {
        return new OutboxServiceImpl(new OutboxDaoImpl(outboxEventRepository), HObjectMapper.NG_DEFAULT_OBJECT_MAPPER);
      }

      @Provides
      @Named("yaml-schema-mapper")
      @Singleton
      public ObjectMapper getYamlSchemaObjectMapper() {
        ObjectMapper objectMapper = Jackson.newObjectMapper();
        HObjectMapper.configureObjectMapperForNG(objectMapper);
        objectMapper.registerModule(new TemplateServiceJacksonModule());
        return objectMapper;
      }

      @Provides
      @Named("disableDeserialization")
      @Singleton
      public boolean getSerializationForDelegate() {
        return false;
      }
    });

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(HPersistence.class).to(MongoPersistence.class);
        bind(new TypeLiteral<Supplier<DelegateCallbackToken>>() {
        }).toInstance(Suppliers.ofInstance(DelegateCallbackToken.newBuilder().build()));
        bind(DelegateServiceGrpcClient.class).toInstance(mock(DelegateServiceGrpcClient.class));
        bind(DelegateSyncService.class).toInstance(mock(DelegateSyncService.class));
        bind(DelegateAsyncService.class).toInstance(mock(DelegateAsyncService.class));
        bind(new TypeLiteral<DelegateServiceGrpc.DelegateServiceBlockingStub>() {
        }).toInstance(DelegateServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName(generateUuid()).build()));
        bind(GitAwarePersistence.class).to(NoOpGitAwarePersistenceImpl.class);
        bind(GitSyncSdkService.class).to(NoOpGitSyncSdkServiceImpl.class);
        bind(TemplateGitXService.class).to(NoOpTemplateGitXServiceImpl.class);
        bind(TelemetryReporter.class).toInstance(mock(TelemetryReporter.class));
        bind(HarnessToGitPushInfoServiceGrpc.HarnessToGitPushInfoServiceBlockingStub.class)
            .toInstance(Mockito.mock(HarnessToGitPushInfoServiceGrpc.HarnessToGitPushInfoServiceBlockingStub.class));
        bind(AccountClient.class).toInstance(mock(AccountClient.class));
        bind(CustomDeploymentResourceClient.class).toInstance(mock(CustomDeploymentResourceClient.class));
        bind(NGTemplateFeatureFlagHelperService.class).toInstance(mock(NGTemplateFeatureFlagHelperService.class));
        bind(Executor.class)
            .annotatedWith(Names.named("TemplateServiceHelperExecutorService"))
            .toInstance(new ManagedExecutorService(Executors.newFixedThreadPool(5)));
      }
    });

    CacheConfigBuilder cacheConfigBuilder =
        CacheConfig.builder().disabledCaches(new HashSet<>()).cacheNamespace("harness-cache");
    if (annotations.stream().anyMatch(annotation -> annotation instanceof Cache)) {
      cacheConfigBuilder.cacheBackend(CAFFEINE);
    } else {
      cacheConfigBuilder.cacheBackend(NOOP);
    }
    CacheModule cacheModule = new CacheModule(cacheConfigBuilder.build());
    modules.add(cacheModule);
    modules.add(PrimaryVersionManagerModule.getInstance());
    modules.add(TimeModule.getInstance());
    modules.add(new GitSyncablePersistenceTestModule());
    modules.add(TestMongoModule.getInstance());
    modules.add(mongoTypeModule(annotations));
    modules.add(new FiltersModule());
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
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object obj) {
    return applyInjector(log, statement, frameworkMethod, obj);
  }
}
