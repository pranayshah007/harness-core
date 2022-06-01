/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.executionplan.rule;

import static io.harness.cache.CacheBackend.CAFFEINE;
import static io.harness.cache.CacheBackend.NOOP;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.mockito.Mockito.mock;

import io.harness.CIExecutionServiceModule;
import io.harness.CIExecutionTestModule;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.CacheConfig;
import io.harness.cache.CacheConfig.CacheConfigBuilder;
import io.harness.cache.CacheModule;
import io.harness.callback.DelegateCallbackToken;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.CIStepConfig;
import io.harness.ci.config.StepImageConfig;
import io.harness.delegate.DelegateServiceGrpc;
import io.harness.engine.pms.tasks.NgDelegate2TaskExecutor;
import io.harness.entitysetupusageclient.EntitySetupUsageClientModule;
import io.harness.exception.exceptionmanager.exceptionhandler.CILiteEngineExceptionHandler;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.factory.ClosingFactory;
import io.harness.factory.ClosingFactoryModule;
import io.harness.ff.CIFeatureFlagNoopServiceImpl;
import io.harness.ff.CIFeatureFlagService;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.mongo.MongoPersistence;
import io.harness.persistence.HPersistence;
import io.harness.pms.sdk.PmsSdkConfiguration;
import io.harness.pms.sdk.PmsSdkModule;
import io.harness.pms.sdk.core.SdkDeployMode;
import io.harness.queue.QueueController;
import io.harness.redis.RedisConfig;
import io.harness.registrars.ExecutionAdvisers;
import io.harness.registrars.ExecutionRegistrar;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.Cache;
import io.harness.rule.InjectorRuleMixin;
import io.harness.springdata.SpringPersistenceTestModule;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.time.TimeModule;

import ci.pipeline.execution.OrchestrationExecutionEventHandlerRegistrar;
import com.google.common.base.Suppliers;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.grpc.inprocess.InProcessChannelBuilder;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

/**
 * Initiates mongo connection and register classes for running UTs
 */

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIExecutionRule implements MethodRule, InjectorRuleMixin, MongoRuleMixin {
  ClosingFactory closingFactory;
  @Rule public CIExecutionTestModule testRule = new CIExecutionTestModule();
  public CIExecutionRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());

    List<Module> modules = new ArrayList<>();
    modules.add(new ClosingFactoryModule(closingFactory));
    modules.add(mongoTypeModule(annotations));
    modules.add(new CIExecutionTestModule());
    modules.add(new EntitySetupUsageClientModule(
        ServiceHttpClientConfig.builder().baseUrl("http://localhost:7457/").build(), "test_secret", "Service"));
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(HPersistence.class).to(MongoPersistence.class);
      }
    });
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(CIFeatureFlagService.class).to(CIFeatureFlagNoopServiceImpl.class);
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

    modules.add(TestMongoModule.getInstance());
    modules.add(new SpringPersistenceTestModule());
    CIStepConfig ciStepConfig =
        CIStepConfig.builder()
            .gitCloneConfig(StepImageConfig.builder().image("gc:1.2.3").build())
            .buildAndPushDockerRegistryConfig(StepImageConfig.builder().image("bpdr:1.2.3").build())
            .buildAndPushECRConfig(StepImageConfig.builder().image("bpecr:1.2.3").build())
            .buildAndPushGCRConfig(StepImageConfig.builder().image("bpgcr:1.2.3").build())
            .gcsUploadConfig(StepImageConfig.builder().image("gcsupload:1.2.3").build())
            .s3UploadConfig(StepImageConfig.builder().image("s3upload:1.2.3").build())
            .artifactoryUploadConfig(StepImageConfig.builder().image("art:1.2.3").build())
            .securityConfig(StepImageConfig.builder().image("sc:1.2.3").build())
            .cacheGCSConfig(StepImageConfig.builder().image("cachegcs:1.2.3").build())
            .cacheS3Config(StepImageConfig.builder().image("caches3:1.2.3").build())
            .gcsUploadConfig(StepImageConfig.builder().image("gcsUpload:1.2.3").build())
            .build();

    modules.add(new CIExecutionServiceModule(CIExecutionServiceConfig.builder()
                                                 .addonImageTag("v1.4-alpha")
                                                 .defaultCPULimit(200)
                                                 .defaultInternalImageConnector("account.harnessimage")
                                                 .defaultMemoryLimit(200)
                                                 .delegateServiceEndpointVariableValue("delegate-service:8080")
                                                 .liteEngineImageTag("v1.4-alpha")
                                                 .addonImage("harness/ci-addon:1.4.0")
                                                 .liteEngineImage("harness/ci-lite-engine:1.4.0")
                                                 .pvcDefaultStorageSize(25600)
                                                 .stepConfig(ciStepConfig)
                                                 .build(),
        false));
    modules.add(TimeModule.getInstance());
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

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        MapBinder<Class<? extends Exception>, ExceptionHandler> exceptionHandlerMapBinder = MapBinder.newMapBinder(
            binder(), new TypeLiteral<Class<? extends Exception>>() {}, new TypeLiteral<ExceptionHandler>() {});
        CILiteEngineExceptionHandler.exceptions().forEach(
            exception -> exceptionHandlerMapBinder.addBinding(exception).to(CILiteEngineExceptionHandler.class));

        bind(new TypeLiteral<Supplier<DelegateCallbackToken>>() {
        }).toInstance(Suppliers.ofInstance(DelegateCallbackToken.newBuilder().build()));

        bind(new TypeLiteral<DelegateServiceGrpc.DelegateServiceBlockingStub>() {
        }).toInstance(DelegateServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName(generateUuid()).build()));
        bind(String.class).annotatedWith(Names.named("ngBaseUrl")).to(String.class);
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      protected NgDelegate2TaskExecutor ngDelegate2TaskExecutor() {
        return mock(NgDelegate2TaskExecutor.class);
      }

      @Provides
      @Named("disableDeserialization")
      @Singleton
      public boolean getSerializationForDelegate() {
        return false;
      }
    });
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
