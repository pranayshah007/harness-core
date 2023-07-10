/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import static io.harness.authorization.AuthorizationServiceHeader.IACM_MANAGER;
import static io.harness.ci.utils.HostedVmSecretResolver.SECRET_CACHE_KEY;
import static io.harness.lock.DistributedLockImplementation.MONGO;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;

import io.harness.AccessControlClientModule;
import io.harness.account.AccountClientModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.impl.IACMYamlSchemaServiceImpl;
import io.harness.app.intfc.IACMYamlSchemaService;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsClientImpl;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.cache.NoOpCache;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
import io.harness.ci.CIExecutionServiceModule;
import io.harness.ci.beans.entities.EncryptedDataDetails;
import io.harness.ci.buildstate.SecretDecryptorViaNg;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.ff.impl.CIFeatureFlagServiceImpl;
import io.harness.ci.logserviceclient.CILogServiceClientModule;
import io.harness.ci.tiserviceclient.TIServiceClientModule;
import io.harness.ci.validation.CIAccountValidationService;
import io.harness.ci.validation.CIAccountValidationServiceImpl;
import io.harness.ci.validation.CIYAMLSanitizationService;
import io.harness.ci.validation.CIYAMLSanitizationServiceImpl;
import io.harness.cistatus.service.GithubService;
import io.harness.cistatus.service.GithubServiceImpl;
import io.harness.cistatus.service.azurerepo.AzureRepoService;
import io.harness.cistatus.service.azurerepo.AzureRepoServiceImpl;
import io.harness.cistatus.service.bitbucket.BitbucketService;
import io.harness.cistatus.service.bitbucket.BitbucketServiceImpl;
import io.harness.cistatus.service.gitlab.GitlabService;
import io.harness.cistatus.service.gitlab.GitlabServiceImpl;
import io.harness.concurrent.HTimeLimiter;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.enforcement.client.EnforcementClientModule;
import io.harness.entitysetupusageclient.EntitySetupUsageClientModule;
import io.harness.grpc.DelegateServiceDriverGrpcClientModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.grpc.client.AbstractManagerGrpcClientModule;
import io.harness.grpc.client.ManagerGrpcClientModule;
import io.harness.iacmserviceclient.IACMServiceClientModule;
import io.harness.impl.scm.ScmServiceClientImpl;
import io.harness.licence.IACMLicenseNoopServiceImpl;
import io.harness.licence.impl.IACMLicenseServiceImpl;
import io.harness.licensing.remote.NgLicenseHttpClientModule;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.mongo.MongoPersistence;
import io.harness.opaclient.OpaClientModule;
import io.harness.packages.HarnessPackages;
import io.harness.persistence.HPersistence;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.redis.RedisConfig;
import io.harness.remote.client.ClientMode;
import io.harness.secrets.SecretDecryptor;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.service.ScmServiceClient;
import io.harness.ssca.client.SSCAServiceClientModuleV2;
import io.harness.stoserviceclient.STOServiceClientModule;
import io.harness.telemetry.AbstractTelemetryModule;
import io.harness.telemetry.TelemetryConfiguration;
import io.harness.threading.ThreadPool;
import io.harness.token.TokenClientModule;
import io.harness.user.UserClientModule;
import io.harness.version.VersionModule;
import io.harness.waiter.AsyncWaitEngineImpl;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.dropwizard.jackson.Jackson;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

@Slf4j
@OwnedBy(HarnessTeam.IACM)
public class IACMManagerServiceModule extends AbstractModule {
  private final IACMManagerConfiguration iacmManagerConfiguration;
  private static final String IACM_MANAGER_NAME = "IACMManager";

  public IACMManagerServiceModule(IACMManagerConfiguration iacmManagerConfiguration) {
    this.iacmManagerConfiguration = iacmManagerConfiguration;
  }

  // TODO: Do we need this?
  @Provides
  @Singleton
  Supplier<DelegateCallbackToken> getDelegateCallbackTokenSupplier(
      DelegateServiceGrpcClient delegateServiceGrpcClient) {
    return Suppliers.memoize(() -> getDelegateCallbackToken(delegateServiceGrpcClient, iacmManagerConfiguration));
  }

  // Final url returned from this fn would be: https://pr.harness.io/ci-delegate-upgrade/ng/#
  @Provides
  @Singleton
  @Named("ngBaseUrl")
  String getNgBaseUrl() {
    String apiUrl = iacmManagerConfiguration.getApiUrl();
    if (apiUrl.endsWith("/")) {
      return apiUrl.substring(0, apiUrl.length() - 1);
    }
    return apiUrl;
  }

  @Provides
  @Named(SECRET_CACHE_KEY)
  Cache<String, EncryptedDataDetails> getSecretTokenCache() {
    return new NoOpCache<>();
  }

  private DelegateCallbackToken getDelegateCallbackToken(
      DelegateServiceGrpcClient delegateServiceClient, IACMManagerConfiguration appConfig) {
    log.info("Generating Delegate callback token");
    final DelegateCallbackToken delegateCallbackToken = delegateServiceClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix("iacmManager")
                                  .setConnection(appConfig.getHarnessIACMMongo().getUri())
                                  .build())
            .build());
    log.info("Delegate callback token generated =[{}]", delegateCallbackToken.getToken());
    return delegateCallbackToken;
  }

  @Provides
  @Singleton
  public AsyncWaitEngine asyncWaitEngine(WaitNotifyEngine waitNotifyEngine) {
    return new AsyncWaitEngineImpl(waitNotifyEngine, NG_ORCHESTRATION);
  }

  @Provides
  @Named("yaml-schema-mapper")
  @Singleton
  public ObjectMapper getYamlSchemaObjectMapper() {
    ObjectMapper objectMapper = Jackson.newObjectMapper();
    IACMManagerApplication.configureObjectMapper(objectMapper);
    return objectMapper;
  }

  @Provides
  @Named("yaml-schema-subtypes")
  @Singleton
  public Map<Class<?>, Set<Class<?>>> yamlSchemaSubtypes() {
    Reflections reflections = new Reflections(HarnessPackages.IO_HARNESS);

    Set<Class<? extends StepSpecType>> subTypesOfStepSpecType = reflections.getSubTypesOf(StepSpecType.class);
    Set<Class<?>> set = new HashSet<>(subTypesOfStepSpecType);

    return ImmutableMap.of(StepSpecType.class, set);
  }

  @Provides
  @Singleton
  public TimeLimiter timeLimiter(ExecutorService executorService) {
    return HTimeLimiter.create(executorService);
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return iacmManagerConfiguration.getDistributedLockImplementation() == null
        ? MONGO
        : iacmManagerConfiguration.getDistributedLockImplementation();
  }

  @Provides
  @Named("lock")
  @Singleton
  RedisConfig redisConfig() {
    return iacmManagerConfiguration.getRedisLockConfig();
  }

  @Override
  protected void configure() {
    install(VersionModule.getInstance()); // gives a /version endpoint for the API
    bind(IACMManagerConfiguration.class).toInstance(iacmManagerConfiguration);
    bind(HPersistence.class).to(MongoPersistence.class).in(Singleton.class);
    bind(IACMYamlSchemaService.class)
        .to(IACMYamlSchemaServiceImpl.class)
        .in(Singleton.class); // HTTP service used by the Pipeline service to retrieve yaml information
    bind(CIFeatureFlagService.class)
        .to(CIFeatureFlagServiceImpl.class)
        .in(Singleton.class); // Used in order to use the feature flags, may need to implement our own
    bind(ScmServiceClient.class).to(ScmServiceClientImpl.class);
    bind(GithubService.class).to(GithubServiceImpl.class);
    bind(GitlabService.class).to(GitlabServiceImpl.class); // same?
    bind(BitbucketService.class).to(BitbucketServiceImpl.class); // same?
    bind(AzureRepoService.class).to(AzureRepoServiceImpl.class); // same?
    bind(SecretDecryptor.class).to(SecretDecryptorViaNg.class); // same?
    bind(AwsClient.class).to(AwsClientImpl.class); // same?
    if (iacmManagerConfiguration.isLocal()) {
      bind(CILicenseService.class)
          .to(IACMLicenseNoopServiceImpl.class)
          .in(Singleton.class); // Do we need our own implementation of this?
    } else {
      bind(CILicenseService.class)
          .to(IACMLicenseServiceImpl.class)
          .in(Singleton.class); // Do we need our own implementation of this?
    }
    bind(CIYAMLSanitizationService.class).to(CIYAMLSanitizationServiceImpl.class).in(Singleton.class);
    // Seems that is used to sanitize stuff but dunno what
    // Keeping it to 1 thread to start with. Assuming executor service is used only to
    // serve health checks. If it's being used for other tasks also, max pool size should be increased.
    bind(CIAccountValidationService.class).to(CIAccountValidationServiceImpl.class).in(Singleton.class);
    bind(ExecutorService.class)
        .toInstance(ThreadPool.create(1, 2, 5, TimeUnit.SECONDS,
            new ThreadFactoryBuilder()
                .setNameFormat("default-iacm-executor-%d")
                .setPriority(Thread.MIN_PRIORITY)
                .build()));

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("async-taskPollExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(
            iacmManagerConfiguration.getAsyncDelegateResponseConsumption().getCorePoolSize(),
            new ThreadFactoryBuilder()
                .setNameFormat("async-taskPollExecutor-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("taskPollExecutor"))
        .toInstance(new ManagedScheduledExecutorService("TaskPoll-Thread"));

    install(NgLicenseHttpClientModule.getInstance(iacmManagerConfiguration.getNgManagerClientConfig(),
        iacmManagerConfiguration.getNgManagerServiceSecret(), IACM_MANAGER.getServiceId())); // Resolve secrets

    install(new CIExecutionServiceModule(iacmManagerConfiguration.getCiExecutionServiceConfig(),
        iacmManagerConfiguration.getShouldConfigureWithPMS())); // CI executor (the engine itself)
    install(DelegateServiceDriverModule.getInstance(false, false));
    install(new DelegateServiceDriverGrpcClientModule(iacmManagerConfiguration.getManagerServiceSecret(),
        iacmManagerConfiguration.getManagerTarget(), iacmManagerConfiguration.getManagerAuthority(), true));

    install(new TokenClientModule(iacmManagerConfiguration.getNgManagerClientConfig(),
        iacmManagerConfiguration.getNgManagerServiceSecret(),
        IACM_MANAGER.getServiceId())); // token generation to communicate with NG manager
    install(PersistentLockModule.getInstance());
    install(new OpaClientModule(iacmManagerConfiguration.getOpaClientConfig(),
        iacmManagerConfiguration.getPolicyManagerSecret(), IACM_MANAGER.getServiceId()));
    install(
        new SSCAServiceClientModuleV2(iacmManagerConfiguration.getSscaServiceConfig(), IACM_MANAGER.getServiceId()));
    install(new AbstractManagerGrpcClientModule() {
      @Override
      public ManagerGrpcClientModule.Config config() {
        return ManagerGrpcClientModule.Config.builder()
            .target(iacmManagerConfiguration.getManagerTarget())
            .authority(iacmManagerConfiguration.getManagerAuthority())
            .build();
      }

      @Override
      public String application() {
        return IACM_MANAGER_NAME;
      }
    });

    install(AccessControlClientModule.getInstance(
        iacmManagerConfiguration.getAccessControlClientConfiguration(), IACM_MANAGER.getServiceId()));
    install(new EntitySetupUsageClientModule(iacmManagerConfiguration.getNgManagerClientConfig(),
        iacmManagerConfiguration.getNgManagerServiceSecret(), IACM_MANAGER_NAME));
    install(new ConnectorResourceClientModule(iacmManagerConfiguration.getNgManagerClientConfig(), // For connectors
        iacmManagerConfiguration.getNgManagerServiceSecret(), IACM_MANAGER_NAME, ClientMode.PRIVILEGED));
    install(new SecretNGManagerClientModule(iacmManagerConfiguration.getNgManagerClientConfig(), // For secrets
        iacmManagerConfiguration.getNgManagerServiceSecret(), IACM_MANAGER_NAME));
    install(new CILogServiceClientModule(iacmManagerConfiguration.getLogServiceConfig())); // For logging
    install(UserClientModule.getInstance(iacmManagerConfiguration.getManagerClientConfig(),
        iacmManagerConfiguration.getManagerServiceSecret(), IACM_MANAGER.getServiceId())); // Retrieve user information
    install(new TIServiceClientModule(
        iacmManagerConfiguration
            .getTiServiceConfig())); // Dependency needed for
                                     // ciIntegrationStageModifier->CIStepGroupUtils->VMInitialiseTaskPAramBuilder->TIServiceUtils
    install(new STOServiceClientModule(
        iacmManagerConfiguration
            .getStoServiceConfig())); // Dependency needed for
                                      // ciIntegrationStageModifier->CIStepGroupUtils->VMInitialiseTaskPAramBuilder->STOServiceUtils
    install(new IACMServiceClientModule(iacmManagerConfiguration.getIacmServiceConfig()));
    install(new AccountClientModule(iacmManagerConfiguration.getManagerClientConfig(), // Account stuff?
        iacmManagerConfiguration.getNgManagerServiceSecret(), IACM_MANAGER.toString()));
    install(EnforcementClientModule.getInstance(iacmManagerConfiguration.getManagerClientConfig(), // Licencing
        iacmManagerConfiguration.getNgManagerServiceSecret(), IACM_MANAGER.getServiceId(),
        iacmManagerConfiguration.getEnforcementClientConfiguration()));
    install(new AbstractTelemetryModule() {
      @Override
      public TelemetryConfiguration telemetryConfiguration() {
        return iacmManagerConfiguration.getSegmentConfiguration();
      }
    });
  }
}
