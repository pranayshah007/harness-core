/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng;

import static io.harness.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.cache.CacheBackend.CAFFEINE;
import static io.harness.cache.CacheBackend.NOOP;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static org.mockito.Mockito.mock;

import io.harness.ModuleType;
import io.harness.OrchestrationModule;
import io.harness.OrchestrationModuleConfig;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.CacheConfig;
import io.harness.cache.CacheConfig.CacheConfigBuilder;
import io.harness.cache.CacheModule;
import io.harness.callback.DelegateCallbackToken;
import io.harness.cdng.orchestration.NgStepRegistrar;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.DelegateServiceGrpc;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.engine.expressions.AmbianceExpressionEvaluatorProvider;
import io.harness.engine.pms.tasks.NgDelegate2TaskExecutor;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.factory.ClosingFactory;
import io.harness.gitsync.persistance.testing.GitSyncablePersistenceTestModule;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.entitysetupusage.EntitySetupUsageModule;
import io.harness.opaclient.OpaServiceClient;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.api.impl.OutboxDaoImpl;
import io.harness.outbox.api.impl.OutboxServiceImpl;
import io.harness.persistence.HPersistence;
import io.harness.pms.sdk.PmsSdkConfiguration;
import io.harness.pms.sdk.PmsSdkModule;
import io.harness.pms.sdk.core.SdkDeployMode;
import io.harness.pms.serializer.jackson.PmsBeansJacksonModule;
import io.harness.queue.QueueController;
import io.harness.redis.RedisConfig;
import io.harness.registrars.CDServiceAdviserRegistrar;
import io.harness.remote.client.ClientMode;
import io.harness.repositories.outbox.OutboxEventRepository;
import io.harness.rule.Cache;
import io.harness.rule.InjectorRuleMixin;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.serializer.CDNGRegistrars;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.ManagerRegistrars;
import io.harness.service.intfc.DelegateAsyncService;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.springdata.HTransactionTemplate;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.time.TimeModule;
import io.harness.user.remote.UserClient;
import io.harness.yaml.YamlSdkModule;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.yaml.schema.client.YamlSchemaClientModule;
import io.harness.yaml.schema.client.config.YamlSchemaClientConfig;

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
import io.dropwizard.jackson.Jackson;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.serializer.HObjectMapper;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.mockito.Mockito;
import org.mongodb.morphia.converters.TypeConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class CDNGTestRule implements InjectorRuleMixin, MethodRule, MongoRuleMixin {
  ClosingFactory closingFactory;

  public CDNGTestRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());

    List<Module> modules = new ArrayList<>();
    modules.add(KryoModule.getInstance());
    modules.add(YamlSdkModule.getInstance());
    modules.add(new GitSyncablePersistenceTestModule());
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder().addAll(CDNGRegistrars.kryoRegistrars).build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(CDNGRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(ManagerRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
            .addAll(ManagerRegistrars.springConverters)
            .build();
      }

      @Provides
      @Singleton
      List<YamlSchemaRootClass> yamlSchemaRootClass() {
        return ImmutableList.<YamlSchemaRootClass>builder().addAll(CDNGRegistrars.yamlSchemaRegistrars).build();
      }

      @Provides
      @Named(OUTBOX_TRANSACTION_TEMPLATE)
      @Singleton
      TransactionTemplate getTransactionTemplate(MongoTransactionManager mongoTransactionManager) {
        return new HTransactionTemplate(mongoTransactionManager, false);
      }

      @Provides
      @Named("yaml-schema-mapper")
      @Singleton
      public ObjectMapper getYamlSchemaObjectMapper() {
        ObjectMapper objectMapper = Jackson.newObjectMapper();
        HObjectMapper.configureObjectMapperForNG(objectMapper);
        objectMapper.registerModule(new PmsBeansJacksonModule());
        return objectMapper;
      }

      @Provides
      @Singleton
      OutboxService getOutboxService(OutboxEventRepository outboxEventRepository) {
        return new OutboxServiceImpl(new OutboxDaoImpl(outboxEventRepository), NG_DEFAULT_OBJECT_MAPPER);
      }

      @Provides
      @Named("yaml-schema-subtypes")
      @Singleton
      public Map<Class<?>, Set<Class<?>>> yamlSchemaSubtypes() {
        return Mockito.mock(Map.class);
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
        bind(ConnectorService.class)
            .annotatedWith(Names.named(DEFAULT_CONNECTOR_SERVICE))
            .toInstance(Mockito.mock(ConnectorService.class));
        bind(SecretManagerClientService.class).toInstance(mock(SecretManagerClientService.class));
        bind(DSLContext.class).toInstance(mock(DSLContext.class));
        bind(DelegateServiceGrpcClient.class).toInstance(mock(DelegateServiceGrpcClient.class));
        bind(DelegateSyncService.class).toInstance(mock(DelegateSyncService.class));
        bind(DelegateAsyncService.class).toInstance(mock(DelegateAsyncService.class));
        bind(UserClient.class).toInstance(mock(UserClient.class));
        bind(OpaServiceClient.class).toInstance(mock(OpaServiceClient.class));
        bind(EntitySetupUsageClient.class).toInstance(mock(EntitySetupUsageClient.class));
        bind(EnforcementClientService.class).toInstance(mock(EnforcementClientService.class));
        bind(AccountClient.class).toInstance(mock(AccountClient.class));
        bind(new TypeLiteral<Supplier<DelegateCallbackToken>>() {
        }).toInstance(Suppliers.ofInstance(DelegateCallbackToken.newBuilder().build()));
        bind(new TypeLiteral<DelegateServiceGrpc.DelegateServiceBlockingStub>() {
        }).toInstance(DelegateServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName(generateUuid()).build()));
        bind(Producer.class)
            .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
            .toInstance(mock(NoOpProducer.class));
        bind(Producer.class)
            .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
            .toInstance(mock(NoOpProducer.class));
        bind(SecretNGManagerClient.class)
            .annotatedWith(Names.named(ClientMode.PRIVILEGED.name()))
            .toInstance(mock(SecretNGManagerClient.class));
      }
    });
    modules.add(TimeModule.getInstance());
    modules.add(NGModule.getInstance());
    modules.add(TestMongoModule.getInstance());
    modules.add(OrchestrationModule.getInstance(getOrchestrationConfig()));
    modules.add(mongoTypeModule(annotations));
    modules.add(new EntitySetupUsageModule());
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
    modules.add(PersistentLockModule.getInstance());

    CacheConfigBuilder cacheConfigBuilder =
        CacheConfig.builder().disabledCaches(new HashSet<>()).cacheNamespace("harness-cache");
    if (annotations.stream().anyMatch(annotation -> annotation instanceof Cache)) {
      cacheConfigBuilder.cacheBackend(CAFFEINE);
    } else {
      cacheConfigBuilder.cacheBackend(NOOP);
    }
    CacheModule cacheModule = new CacheModule(cacheConfigBuilder.build());
    modules.add(cacheModule);

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

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      protected NgDelegate2TaskExecutor ngDelegate2TaskExecutor() {
        return mock(NgDelegate2TaskExecutor.class);
      }
    });
    modules.add(PmsSdkModule.getInstance(getPmsSdkConfiguration()));
    modules.add(YamlSchemaClientModule.getInstance(getYamlSchemaClientConfig(), NG_MANAGER.getServiceId()));
    return modules;
  }
  private YamlSchemaClientConfig getYamlSchemaClientConfig() {
    return YamlSchemaClientConfig.builder().build();
  }

  private PmsSdkConfiguration getPmsSdkConfiguration() {
    return PmsSdkConfiguration.builder()
        .deploymentMode(SdkDeployMode.LOCAL)
        .moduleType(ModuleType.CD)
        .engineSteps(NgStepRegistrar.getEngineSteps())
        .engineAdvisers(CDServiceAdviserRegistrar.getEngineAdvisers())
        .build();
  }

  private OrchestrationModuleConfig getOrchestrationConfig() {
    return OrchestrationModuleConfig.builder()
        .serviceName("CD_NG_TEST")
        .expressionEvaluatorProvider(new AmbianceExpressionEvaluatorProvider())
        .build();
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
  public Statement apply(Statement base, FrameworkMethod method, Object target) {
    return applyInjector(log, base, method, target);
  }
}
