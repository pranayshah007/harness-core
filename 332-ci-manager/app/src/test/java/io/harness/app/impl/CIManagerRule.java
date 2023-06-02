/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.impl;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.cache.CacheBackend.CAFFEINE;
import static io.harness.cache.CacheBackend.NOOP;

import io.harness.AccessControlClientConfiguration;
import io.harness.ModuleType;
import io.harness.SCMGrpcClientModule;
import io.harness.ScmConnectionConfig;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.CIManagerConfiguration;
import io.harness.app.CIManagerConfigurationOverride;
import io.harness.app.CIManagerServiceModule;
import io.harness.app.PrimaryVersionManagerModule;
import io.harness.beans.entities.IACMServiceConfig;
import io.harness.cache.CacheConfig;
import io.harness.cache.CacheConfig.CacheConfigBuilder;
import io.harness.cache.CacheModule;
import io.harness.ci.beans.entities.LogServiceConfig;
import io.harness.ci.beans.entities.TIServiceConfig;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.OrchestrationExecutionEventHandlerRegistrar;
import io.harness.ci.registrars.ExecutionAdvisers;
import io.harness.ci.registrars.ExecutionRegistrar;
import io.harness.ci.serializer.CiExecutionRegistrars;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.factory.ClosingFactory;
import io.harness.factory.ClosingFactoryModule;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.mongo.MongoConfig;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.pms.sdk.PmsSdkConfiguration;
import io.harness.pms.sdk.PmsSdkModule;
import io.harness.pms.sdk.core.SdkDeployMode;
import io.harness.redis.RedisConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.Cache;
import io.harness.rule.InjectorRuleMixin;
import io.harness.serializer.CiBeansRegistrars;
import io.harness.serializer.ConnectorNextGenRegistrars;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.PersistenceRegistrars;
import io.harness.serializer.PrimaryVersionManagerRegistrars;
import io.harness.serializer.YamlBeansModuleRegistrars;
import io.harness.springdata.SpringPersistenceTestModule;
import io.harness.ssca.beans.entities.SSCAServiceConfig;
import io.harness.sto.beans.entities.STOServiceConfig;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPoolConfig;
import io.harness.yaml.YamlSdkModule;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
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
import org.springframework.core.convert.converter.Converter;

@Slf4j
@OwnedBy(CI)
public class CIManagerRule implements MethodRule, InjectorRuleMixin, MongoRuleMixin {
  ClosingFactory closingFactory;

  public CIManagerRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());

    List<Module> modules = new ArrayList<>();
    modules.add(YamlSdkModule.getInstance());
    modules.add(PrimaryVersionManagerModule.getInstance());
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> registrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(YamlBeansModuleRegistrars.kryoRegistrars)
            .addAll(CiBeansRegistrars.kryoRegistrars)
            .addAll(CiExecutionRegistrars.kryoRegistrars)
            .addAll(ConnectorNextGenRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(CiExecutionRegistrars.morphiaRegistrars)
            .addAll(PrimaryVersionManagerRegistrars.morphiaRegistrars)
            .build();
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
      MongoConfig mongoConfig() {
        return MongoConfig.builder().build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
            .addAll(CiExecutionRegistrars.springConverters)
            .build();
      }

      @Provides
      @Singleton
      List<YamlSchemaRootClass> yamlSchemaRootClass() {
        return ImmutableList.<YamlSchemaRootClass>builder().addAll(CiBeansRegistrars.yamlSchemaRegistrars).build();
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

    CIManagerConfiguration configuration =
        CIManagerConfiguration.builder()
            .eventsFrameworkConfiguration(
                EventsFrameworkConfiguration.builder()
                    .redisConfig(RedisConfig.builder().envNamespace("blah").redisUrl("dummyRedisUrl").build())
                    .build())
            .managerAuthority("localhost")
            .managerTarget("localhost:9880")
            .accessControlClientConfiguration(AccessControlClientConfiguration.builder()
                                                  .enableAccessControl(false)
                                                  .accessControlServiceSecret("token")
                                                  .accessControlServiceConfig(ServiceHttpClientConfig.builder()
                                                                                  .baseUrl("http://localhost:9006/api/")
                                                                                  .readTimeOutSeconds(15)
                                                                                  .connectTimeOutSeconds(15)
                                                                                  .build())
                                                  .build())
            .ciExecutionServiceConfig(
                CIExecutionServiceConfig.builder()
                    .addonImageTag("v1.4-alpha")
                    .queueServiceClientConfig(
                        QueueServiceClientConfig.builder()
                            .httpClientConfig(
                                ServiceHttpClientConfig.builder().baseUrl("http://localhost:7457/").build())
                            .queueServiceSecret("tokrn")
                            .envNamespace("localhost")
                            .build())
                    .defaultCPULimit(200)
                    .defaultInternalImageConnector("account.harnessimage")
                    .defaultMemoryLimit(200)
                    .delegateServiceEndpointVariableValue("delegate-service:8080")
                    .liteEngineImageTag("v1.4-alpha")
                    .pvcDefaultStorageSize(25600)
                    .build())
            .asyncDelegateResponseConsumption(ThreadPoolConfig.builder().corePoolSize(1).build())
            .logServiceConfig(
                LogServiceConfig.builder().baseUrl("http://localhost-inc:8079").globalToken("global-token").build())
            .tiServiceConfig(
                TIServiceConfig.builder().baseUrl("http://localhost-inc:8078").globalToken("global-token").build())
            .stoServiceConfig(
                STOServiceConfig.builder().baseUrl("http://localhost-inc:4000").globalToken("global-token").build())
            .iacmServiceConfig(
                IACMServiceConfig.builder().baseUrl("http://localhost-inc:5000").globalToken("global-token").build())
            .sscaServiceConfig(
                SSCAServiceConfig.builder()
                    .httpClientConfig(ServiceHttpClientConfig.builder().baseUrl("http://localhost:8186").build())
                    .build())
            .scmConnectionConfig(ScmConnectionConfig.builder().url("localhost:8181").build())
            .managerServiceSecret("IC04LYMBf1lDP5oeY4hupxd4HJhLmN6azUku3xEbeE3SUx5G3ZYzhbiwVtK4i7AmqyU9OZkwB4v8E9qM")
            .ngManagerClientConfig(ServiceHttpClientConfig.builder().baseUrl("http://localhost:7457/").build())
            .managerClientConfig(ServiceHttpClientConfig.builder().baseUrl("http://localhost:3457/").build())
            .ngManagerServiceSecret("IC04LYMBf1lDP5oeY4hupxd4HJhLmN6azUku3xEbeE3SUx5G3ZYzhbiwVtK4i7AmqyU9OZkwB4v8E9qM")
            .apiUrl("https://localhost:8181/#/")
            .build();

    modules.add(new SCMGrpcClientModule(configuration.getScmConnectionConfig()));
    modules.add(new ClosingFactoryModule(closingFactory));
    modules.add(mongoTypeModule(annotations));
    modules.add(TestMongoModule.getInstance());
    modules.add(new SpringPersistenceTestModule());
    modules.add(new CIManagerServiceModule(configuration, new CIManagerConfigurationOverride()));
    modules.add(PmsSdkModule.getInstance(getPmsSdkConfiguration()));
    return modules;
  }

  private PmsSdkConfiguration getPmsSdkConfiguration() {
    return PmsSdkConfiguration.builder()
        .deploymentMode(SdkDeployMode.LOCAL)
        .moduleType(ModuleType.CI)
        .engineSteps(ExecutionRegistrar.getEngineSteps())
        .engineAdvisers(ExecutionAdvisers.getEngineAdvisers())
        .engineEventHandlersMap(OrchestrationExecutionEventHandlerRegistrar.getEngineEventHandlers())
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
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object target) {
    return applyInjector(log, statement, frameworkMethod, target);
  }
}
