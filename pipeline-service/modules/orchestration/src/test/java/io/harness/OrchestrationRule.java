/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.cache.CacheBackend.CAFFEINE;
import static io.harness.cache.CacheBackend.NOOP;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.maintenance.MaintenanceController.forceMaintenance;

import static org.mockito.Mockito.mock;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.CacheConfig;
import io.harness.cache.CacheConfig.CacheConfigBuilder;
import io.harness.cache.CacheModule;
import io.harness.callback.DelegateCallbackToken;
import io.harness.delay.DelayEventListener;
import io.harness.delegate.DelegateServiceGrpc;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.expressions.AmbianceExpressionEvaluatorProvider;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.factory.ClosingFactory;
import io.harness.factory.ClosingFactoryModule;
import io.harness.ff.FeatureFlagService;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.opaclient.OpaServiceClient;
import io.harness.persistence.HPersistence;
import io.harness.pms.sdk.PmsSdkConfiguration;
import io.harness.pms.sdk.PmsSdkModule;
import io.harness.pms.sdk.core.SdkDeployMode;
import io.harness.queue.QueueController;
import io.harness.queue.QueueListenerController;
import io.harness.redis.RedisConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.Cache;
import io.harness.rule.InjectorRuleMixin;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.OrchestrationRegistrars;
import io.harness.serializer.kryo.OrchestrationTestKryoRegistrar;
import io.harness.service.intfc.DelegateAsyncService;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.springdata.HTransactionTemplate;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.time.TimeModule;
import io.harness.user.remote.UserClient;
import io.harness.version.VersionModule;
import io.harness.waiter.NotifierScheduledExecutorService;
import io.harness.waiter.NotifyResponseCleaner;

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
import dev.morphia.converters.TypeConverter;
import io.grpc.inprocess.InProcessChannelBuilder;
import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationRule implements MethodRule, InjectorRuleMixin, MongoRuleMixin {
  ClosingFactory closingFactory;

  public OrchestrationRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) throws Exception {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());

    List<Module> modules = new ArrayList<>();
    modules.add(new ClosingFactoryModule(closingFactory));
    modules.add(KryoModule.getInstance());
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(OrchestrationRegistrars.kryoRegistrars)
            .add(OrchestrationTestKryoRegistrar.class)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(OrchestrationRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(OrchestrationRegistrars.morphiaConverters)
            .build();
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
            .addAll(OrchestrationRegistrars.springConverters)
            .build();
      }
    });

    modules.add(mongoTypeModule(annotations));

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      TransactionTemplate getTransactionTemplate(MongoTransactionManager mongoTransactionManager) {
        return new HTransactionTemplate(mongoTransactionManager, false);
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
        bind(UserClient.class).toInstance(mock(UserClient.class));
        bind(OpaServiceClient.class).toInstance(mock(OpaServiceClient.class));
        bind(DelegateSyncService.class).toInstance(mock(DelegateSyncService.class));
        bind(DelegateAsyncService.class).toInstance(mock(DelegateAsyncService.class));
        bind(AccountClient.class).toInstance(mock(AccountClient.class));
        bind(FeatureFlagService.class).toInstance(mock(FeatureFlagService.class));
        bind(new TypeLiteral<DelegateServiceGrpc.DelegateServiceBlockingStub>() {
        }).toInstance(DelegateServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName(generateUuid()).build()));
        bind(OrchestrationEventEmitter.class).to(MockEventEmitter.class);
        bind(EntitySetupUsageClient.class).toInstance(mock(EntitySetupUsageClient.class));
      }
    });
    ServiceHttpClientConfig serviceHttpClientConfig =
        ServiceHttpClientConfig.builder().baseUrl("http://localhost:7457/").build();
    modules.add(AccessControlClientModule.getInstance(AccessControlClientConfiguration.builder()
                                                          .enableAccessControl(true)
                                                          .accessControlServiceSecret("secret")
                                                          .accessControlServiceConfig(serviceHttpClientConfig)
                                                          .build(),
        PIPELINE_SERVICE.getServiceId()));
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      DistributedLockImplementation distributedLockImplementation() {
        return DistributedLockImplementation.NOOP;
      }

      @Provides
      @Named("lock")
      @Singleton
      RedisConfig redisConfig() {
        return RedisConfig.builder().build();
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      @Named("PRIVILEGED")
      AccountClient getAccountClient() {
        return mock(AccountClient.class);
      }
    });

    modules.add(PersistentLockModule.getInstance());

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
      }
    });
    modules.add(VersionModule.getInstance());
    modules.add(TimeModule.getInstance());
    modules.add(TestMongoModule.getInstance());
    modules.add(new OrchestrationPersistenceTestModule());
    modules.add(
        OrchestrationModule.getInstance(OrchestrationModuleConfig.builder()
                                            .serviceName("ORCHESTRATION_TEST")
                                            .expressionEvaluatorProvider(new AmbianceExpressionEvaluatorProvider())
                                            .isPipelineService(true)
                                            .build()));
    CacheConfigBuilder cacheConfigBuilder =
        CacheConfig.builder().disabledCaches(new HashSet<>()).cacheNamespace("harness-cache");
    if (annotations.stream().anyMatch(annotation -> annotation instanceof Cache)) {
      cacheConfigBuilder.cacheBackend(CAFFEINE);
    } else {
      cacheConfigBuilder.cacheBackend(NOOP);
    }
    CacheModule cacheModule = new CacheModule(cacheConfigBuilder.build());
    modules.add(cacheModule);
    PmsSdkConfiguration sdkConfig =
        PmsSdkConfiguration.builder().moduleType(ModuleType.PMS).deploymentMode(SdkDeployMode.LOCAL).build();
    modules.add(PmsSdkModule.getInstance(sdkConfig));
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
    forceMaintenance(false);
    final QueueListenerController queueListenerController = injector.getInstance(QueueListenerController.class);
    queueListenerController.register(injector.getInstance(DelayEventListener.class), 1);

    closingFactory.addServer(new Closeable() {
      @SneakyThrows
      @Override
      public void close() {
        queueListenerController.stop();
      }
    });

    NotifierScheduledExecutorService notifierScheduledExecutorService =
        injector.getInstance(NotifierScheduledExecutorService.class);
    notifierScheduledExecutorService.scheduleWithFixedDelay(
        injector.getInstance(NotifyResponseCleaner.class), 0L, 1000L, TimeUnit.MILLISECONDS);
    closingFactory.addServer(new Closeable() {
      @Override
      public void close() throws IOException {
        notifierScheduledExecutorService.shutdown();
      }
    });
  }

  @Override
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object target) {
    return applyInjector(log, statement, frameworkMethod, target);
  }
}
