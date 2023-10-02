/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.RESOUCE_GROUP_SERVICE;
import static io.harness.lock.DistributedLockImplementation.NOOP;
import static io.harness.outbox.OutboxSDKConstants.DEFAULT_OUTBOX_POLL_CONFIGURATION;

import static java.lang.System.getProperty;

import io.harness.AccessControlClientConfiguration;
import io.harness.AccessControlClientModule;
import io.harness.ResourceGroupResourceClasses;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.client.remote.AuditClientModule;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.factory.ClosingFactory;
import io.harness.factory.ClosingFactoryModule;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.metrics.modules.MetricsModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.oas.OASModule;
import io.harness.outbox.TransactionOutboxModule;
import io.harness.persistence.HPersistence;
import io.harness.redis.RedisConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.InjectorRuleMixin;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.PersistenceRegistrars;
import io.harness.serializer.morphia.ResourceGroupSerializer;
import io.harness.springdata.HTransactionTemplate;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.time.TimeModule;
import io.harness.version.VersionModule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@OwnedBy(PL)
public class ResourceGroupTestRule implements MethodRule, InjectorRuleMixin, MongoRuleMixin {
  ClosingFactory closingFactory;

  public ResourceGroupTestRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) throws Exception {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());
    List<Module> modules = new ArrayList<>();
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(HPersistence.class).to(MongoPersistence.class);
      }
    });
    modules.add(new ClosingFactoryModule(closingFactory));
    modules.add(KryoModule.getInstance());
    modules.add(mongoTypeModule(annotations));
    modules.add(VersionModule.getInstance());
    modules.add(TimeModule.getInstance());
    modules.add(TestMongoModule.getInstance());
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      DistributedLockImplementation distributedLockImplementation() {
        return NOOP;
      }

      @Provides
      @Named("lock")
      @Singleton
      RedisConfig redisConfig() {
        return RedisConfig.builder().build();
      }

      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return MongoConfig.builder().build();
      }
    });
    modules.add(PersistentLockModule.getInstance());
    modules.add(new ResourceGroupPersistenceTestModule());
    ResourceGroupServiceConfig resourceGroupConfig = getConfiguration();
    modules.add(new AuditClientModule(resourceGroupConfig.getAuditClientConfig(), "secret",
        RESOUCE_GROUP_SERVICE.getServiceId(), resourceGroupConfig.isEnableAudit()));
    modules.add(new ResourceGroupModule(getConfiguration()));
    ServiceHttpClientConfig serviceHttpClientConfig =
        ServiceHttpClientConfig.builder().baseUrl("http://localhost:7457/").build();
    modules.add(AccessControlClientModule.getInstance(AccessControlClientConfiguration.builder()
                                                          .enableAccessControl(true)
                                                          .accessControlServiceSecret("secret")
                                                          .accessControlServiceConfig(serviceHttpClientConfig)
                                                          .build(),
        RESOUCE_GROUP_SERVICE.getServiceId()));

    if (resourceGroupConfig.isExportMetricsToStackDriver()) {
      modules.add(new MetricsModule());
    }
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Producer.class)
            .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
            .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      }
    });
    modules.add(new TransactionOutboxModule(DEFAULT_OUTBOX_POLL_CONFIGURATION, RESOUCE_GROUP_SERVICE.getServiceId(),
        resourceGroupConfig.isExportMetricsToStackDriver()));

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      TransactionTemplate getTransactionTemplate(MongoTransactionManager mongoTransactionManager) {
        return new HTransactionTemplate(mongoTransactionManager, false);
      }

      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(ResourceGroupSerializer.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder().build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(PersistenceRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(ResourceGroupSerializer.morphiaRegistrars)
            .build();
      }
    });
    modules.add(new OASModule() {
      @Override
      public Collection<Class<?>> getResourceClasses() {
        return ResourceGroupResourceClasses.getResourceClasses();
      }
    });
    return modules;
  }

  private ResourceGroupServiceConfig getConfiguration() {
    ResourceGroupServiceConfig configuration = ResourceGroupServiceConfig.builder()
                                                   .enableAudit(true)
                                                   .enableResourceGroup(true)
                                                   .exportMetricsToStackDriver(true)
                                                   .build();
    configuration.setMongoConfig(MongoConfig.builder()
                                     .uri(getProperty("mongoUri",
                                         "mongodb://localhost:27017/"
                                             + "resourcegroup"))
                                     .build());
    configuration.setDistributedLockImplementation(DistributedLockImplementation.NOOP);

    ServiceHttpClientConfig serviceHttpClientConfig =
        ServiceHttpClientConfig.builder().baseUrl("http://localhost:7457/").build();
    configuration.setAuditClientConfig(serviceHttpClientConfig);

    ResourceClientConfigs.ServiceConfig serviceConfig =
        ResourceClientConfigs.ServiceConfig.builder().baseUrl("http://localhost:7457/").secret("secret").build();
    ResourceClientConfigs resourceClientConfigs = ResourceClientConfigs.builder()
                                                      .pipelineService(serviceConfig)
                                                      .resourceGroupService(serviceConfig)
                                                      .manager(serviceConfig)
                                                      .ngManager(serviceConfig)
                                                      .templateService(serviceConfig)
                                                      .gitopsService(serviceConfig)
                                                      .ceNextGen(serviceConfig)
                                                      .code(serviceConfig)
                                                      .build();
    configuration.setResourceClientConfigs(resourceClientConfigs);
    return configuration;
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
