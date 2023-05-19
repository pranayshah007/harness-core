/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.executionplan.rule;

import static io.harness.cache.CacheBackend.CAFFEINE;
import static io.harness.cache.CacheBackend.NOOP;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.mockito.Mockito.mock;

import io.harness.ModuleType;
import io.harness.SCMGrpcClientModule;
import io.harness.ScmConnectionConfig;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsClientImpl;
import io.harness.beans.entities.IACMServiceConfig;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.cache.CacheConfig;
import io.harness.cache.CacheConfig.CacheConfigBuilder;
import io.harness.cache.CacheModule;
import io.harness.callback.DelegateCallbackToken;
import io.harness.ci.CIExecutionServiceModule;
import io.harness.ci.CIExecutionTestModule;
import io.harness.ci.buildstate.SecretDecryptorViaNg;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.CIStepConfig;
import io.harness.ci.config.StepImageConfig;
import io.harness.ci.config.VmImageConfig;
import io.harness.ci.execution.OrchestrationExecutionEventHandlerRegistrar;
import io.harness.ci.ff.CIFeatureFlagNoopServiceImpl;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.license.CILicenseNoopServiceImpl;
import io.harness.ci.registrars.ExecutionAdvisers;
import io.harness.ci.registrars.ExecutionRegistrar;
import io.harness.cistatus.service.GithubService;
import io.harness.cistatus.service.GithubServiceImpl;
import io.harness.cistatus.service.azurerepo.AzureRepoService;
import io.harness.cistatus.service.azurerepo.AzureRepoServiceImpl;
import io.harness.cistatus.service.bitbucket.BitbucketService;
import io.harness.cistatus.service.bitbucket.BitbucketServiceImpl;
import io.harness.cistatus.service.gitlab.GitlabService;
import io.harness.cistatus.service.gitlab.GitlabServiceImpl;
import io.harness.delegate.DelegateServiceGrpc;
import io.harness.entitysetupusageclient.EntitySetupUsageClientModule;
import io.harness.exception.exceptionmanager.exceptionhandler.CILiteEngineExceptionHandler;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.factory.ClosingFactory;
import io.harness.factory.ClosingFactoryModule;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.iacmserviceclient.IACMServiceClient;
import io.harness.iacmserviceclient.IACMServiceClientFactory;
import io.harness.impl.scm.ScmServiceClientImpl;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.persistence.HPersistence;
import io.harness.pms.sdk.PmsSdkConfiguration;
import io.harness.pms.sdk.PmsSdkModule;
import io.harness.pms.sdk.core.SdkDeployMode;
import io.harness.queue.QueueController;
import io.harness.redis.RedisConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.Cache;
import io.harness.rule.InjectorRuleMixin;
import io.harness.secrets.SecretDecryptor;
import io.harness.service.ScmServiceClient;
import io.harness.springdata.SpringPersistenceTestModule;
import io.harness.ssca.beans.entities.SSCAServiceConfig;
import io.harness.ssca.client.SSCAServiceClient;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.time.TimeModule;

import com.google.common.base.Suppliers;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
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
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(CILicenseService.class).to(CILicenseNoopServiceImpl.class);
      }
    });
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(AccountClient.class).toInstance(mock(AccountClient.class));
        bind(AccountClient.class).annotatedWith(Names.named("PRIVILEGED")).toInstance(mock(AccountClient.class));
      }
    });

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ScmServiceClient.class).to(ScmServiceClientImpl.class);
        bind(GithubService.class).to(GithubServiceImpl.class);
        bind(GitlabService.class).to(GitlabServiceImpl.class);
        bind(BitbucketService.class).to(BitbucketServiceImpl.class);
        bind(AzureRepoService.class).to(AzureRepoServiceImpl.class);
        bind(SecretDecryptor.class).to(SecretDecryptorViaNg.class);
        bind(AwsClient.class).to(AwsClientImpl.class);
        bind(IACMServiceConfig.class)
            .toInstance(
                IACMServiceConfig.builder().baseUrl("http://localhost:4000").globalToken("api/v1/token").build());
        bind(SSCAServiceConfig.class)
            .toInstance(
                SSCAServiceConfig.builder()
                    .httpClientConfig(ServiceHttpClientConfig.builder().baseUrl("http://localhost:8186").build())
                    .baseUrl("http://localhost:8186")
                    .globalToken("global-token")
                    .build());
        bind(IACMServiceClient.class).toProvider(IACMServiceClientFactory.class).in(Scopes.SINGLETON);
        bind(SSCAServiceClient.class).toInstance(mock(SSCAServiceClient.class));
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

    VmImageConfig vmImageConfig = VmImageConfig.builder()
                                      .gitClone("vm-gitClone")
                                      .artifactoryUpload("vm-artifactoryUpload")
                                      .s3Upload("vm-s3Upload")
                                      .gcsUpload("vm-gcsUpload")
                                      .buildAndPushDockerRegistry("vm-buildAndPushDockerRegistry")
                                      .buildAndPushECR("vm-buildAndPushECR")
                                      .buildAndPushGCR("vm-buildAndPushGCR")
                                      .cacheGCS("vm-cacheGCS")
                                      .cacheS3("vm-cacheS3")
                                      .security("vm-security")
                                      .build();

    CIStepConfig ciStepConfig =
        CIStepConfig.builder()
            .gitCloneConfig(StepImageConfig.builder().image("gc:1.2.3").build())
            .buildAndPushDockerRegistryConfig(StepImageConfig.builder().image("bpdr:1.2.3").build())
            .buildAndPushACRConfig(StepImageConfig.builder().image("bpacr:1.2.3").build())
            .buildAndPushECRConfig(StepImageConfig.builder().image("bpecr:1.2.3").build())
            .buildAndPushGCRConfig(StepImageConfig.builder().image("bpgcr:1.2.3").build())
            .gcsUploadConfig(StepImageConfig.builder().image("gcsupload:1.2.3").build())
            .s3UploadConfig(StepImageConfig.builder().image("s3upload:1.2.3").build())
            .artifactoryUploadConfig(StepImageConfig.builder().image("art:1.2.3").build())
            .securityConfig(StepImageConfig.builder().image("sc:1.2.3").build())
            .cacheGCSConfig(StepImageConfig.builder().image("cachegcs:1.2.3").build())
            .cacheS3Config(StepImageConfig.builder().image("caches3:1.2.3").build())
            .gcsUploadConfig(StepImageConfig.builder().image("gcsUpload:1.2.3").build())
            .sscaOrchestrationConfig(StepImageConfig.builder().image("sscaorchestrate:0.0.1").build())
            .sscaEnforcementConfig(StepImageConfig.builder().image("sscaEnforcement:0.0.1").build())
            .vmImageConfig(vmImageConfig)
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
                                                 .queueServiceClientConfig(QueueServiceClientConfig.builder().build())
                                                 .build(),
        false));

    modules.add(new ProviderModule() {
      @Provides
      @Named("PRIVILEGED")
      @Singleton
      AccessControlClient accessControlClient() {
        return mock(AccessControlClient.class);
      }
    });
    modules.add(new SCMGrpcClientModule(ScmConnectionConfig.builder().url("dummyurl").build()));

    modules.add(TimeModule.getInstance());
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      DistributedLockImplementation distributedLockImplementation() {
        return DistributedLockImplementation.NOOP;
      }

      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return MongoConfig.builder().build();
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
