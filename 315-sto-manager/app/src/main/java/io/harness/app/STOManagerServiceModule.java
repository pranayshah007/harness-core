/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import static io.harness.authorization.AuthorizationServiceHeader.STO_MANAGER;
import static io.harness.ci.utils.HostedVmSecretResolver.SECRET_CACHE_KEY;
import static io.harness.lock.DistributedLockImplementation.MONGO;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;

import io.harness.AccessControlClientModule;
import io.harness.account.AccountClientModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.impl.STOYamlSchemaServiceImpl;
import io.harness.app.intfc.STOYamlSchemaService;
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
import io.harness.ci.license.impl.CILicenseServiceImpl;
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
@OwnedBy(HarnessTeam.STO)
public class STOManagerServiceModule extends AbstractModule {
  private final CIManagerConfiguration stoManagerConfiguration;

  public STOManagerServiceModule(CIManagerConfiguration stoManagerConfiguration) {
    this.stoManagerConfiguration = stoManagerConfiguration;
  }

  @Provides
  @Singleton
  Supplier<DelegateCallbackToken> getDelegateCallbackTokenSupplier(
      DelegateServiceGrpcClient delegateServiceGrpcClient) {
    return (Supplier<DelegateCallbackToken>) Suppliers.memoize(
        () -> getDelegateCallbackToken(delegateServiceGrpcClient, stoManagerConfiguration));
  }

  // Final url returned from this fn would be: https://pr.harness.io/ci-delegate-upgrade/ng/#
  @Provides
  @Singleton
  @Named("ngBaseUrl")
  String getNgBaseUrl() {
    String apiUrl = stoManagerConfiguration.getApiUrl();
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
      DelegateServiceGrpcClient delegateServiceClient, CIManagerConfiguration appConfig) {
    log.info("Generating Delegate callback token");
    String connectionUri = STOManagerConfiguration.getHarnessSTOMongo(appConfig.getHarnessCIMongo()).getUri();
    final DelegateCallbackToken delegateCallbackToken = delegateServiceClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(
                MongoDatabase.newBuilder().setCollectionNamePrefix("stoManager").setConnection(connectionUri).build())
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
    STOManagerApplication.configureObjectMapper(objectMapper);
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
    return MONGO;
  }

  @Provides
  @Named("lock")
  @Singleton
  RedisConfig redisConfig() {
    return stoManagerConfiguration.getEventsFrameworkConfiguration().getRedisConfig();
  }

  @Override
  protected void configure() {
    install(VersionModule.getInstance());
    bind(HPersistence.class).to(MongoPersistence.class).in(Singleton.class);
    bind(STOYamlSchemaService.class).to(STOYamlSchemaServiceImpl.class).in(Singleton.class);
    bind(CIFeatureFlagService.class).to(CIFeatureFlagServiceImpl.class).in(Singleton.class);
    bind(ScmServiceClient.class).to(ScmServiceClientImpl.class);
    bind(GithubService.class).to(GithubServiceImpl.class);
    bind(GitlabService.class).to(GitlabServiceImpl.class);
    bind(BitbucketService.class).to(BitbucketServiceImpl.class);
    bind(AzureRepoService.class).to(AzureRepoServiceImpl.class);
    bind(SecretDecryptor.class).to(SecretDecryptorViaNg.class);
    bind(AwsClient.class).to(AwsClientImpl.class);
    bind(CILicenseService.class).to(CILicenseServiceImpl.class).in(Singleton.class);
    bind(CIYAMLSanitizationService.class).to(CIYAMLSanitizationServiceImpl.class).in(Singleton.class);
    bind(CIAccountValidationService.class).to(CIAccountValidationServiceImpl.class).in(Singleton.class);
    // Keeping it to 1 thread to start with. Assuming executor service is used only to
    // serve health checks. If it's being used for other tasks also, max pool size should be increased.
    bind(ExecutorService.class)
        .toInstance(ThreadPool.create(1, 2, 5, TimeUnit.SECONDS,
            new ThreadFactoryBuilder()
                .setNameFormat("default-sto-executor-%d")
                .setPriority(Thread.MIN_PRIORITY)
                .build()));

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("async-taskPollExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(
            stoManagerConfiguration.getAsyncDelegateResponseConsumption().getCorePoolSize(),
            new ThreadFactoryBuilder()
                .setNameFormat("async-taskPollExecutor-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("taskPollExecutor"))
        .toInstance(new ManagedScheduledExecutorService("TaskPoll-Thread"));

    install(NgLicenseHttpClientModule.getInstance(stoManagerConfiguration.getNgManagerClientConfig(),
        stoManagerConfiguration.getNgManagerServiceSecret(), STO_MANAGER.getServiceId()));

    install(new CIExecutionServiceModule(
        stoManagerConfiguration.getCiExecutionServiceConfig(), stoManagerConfiguration.getShouldConfigureWithPMS()));
    install(DelegateServiceDriverModule.getInstance(false, false));
    install(new DelegateServiceDriverGrpcClientModule(stoManagerConfiguration.getManagerServiceSecret(),
        stoManagerConfiguration.getManagerTarget(), stoManagerConfiguration.getManagerAuthority(), true));

    install(new TokenClientModule(stoManagerConfiguration.getNgManagerClientConfig(),
        stoManagerConfiguration.getNgManagerServiceSecret(), STO_MANAGER.getServiceId()));
    install(PersistentLockModule.getInstance());

    install(new OpaClientModule(stoManagerConfiguration.getOpaClientConfig(),
        stoManagerConfiguration.getPolicyManagerSecret(), STO_MANAGER.getServiceId()));
    install(new AbstractManagerGrpcClientModule() {
      @Override
      public ManagerGrpcClientModule.Config config() {
        return ManagerGrpcClientModule.Config.builder()
            .target(stoManagerConfiguration.getManagerTarget())
            .authority(stoManagerConfiguration.getManagerAuthority())
            .build();
      }

      @Override
      public String application() {
        return "STOManager";
      }
    });

    install(AccessControlClientModule.getInstance(
        stoManagerConfiguration.getAccessControlClientConfiguration(), STO_MANAGER.getServiceId()));
    install(new EntitySetupUsageClientModule(stoManagerConfiguration.getNgManagerClientConfig(),
        stoManagerConfiguration.getNgManagerServiceSecret(), "STOManager"));
    install(new ConnectorResourceClientModule(stoManagerConfiguration.getNgManagerClientConfig(),
        stoManagerConfiguration.getNgManagerServiceSecret(), "STOManager", ClientMode.PRIVILEGED));
    install(new SecretNGManagerClientModule(stoManagerConfiguration.getNgManagerClientConfig(),
        stoManagerConfiguration.getNgManagerServiceSecret(), "STOManager"));
    install(new CILogServiceClientModule(stoManagerConfiguration.getLogServiceConfig()));
    install(UserClientModule.getInstance(stoManagerConfiguration.getManagerClientConfig(),
        stoManagerConfiguration.getManagerServiceSecret(), STO_MANAGER.getServiceId()));
    install(new TIServiceClientModule(stoManagerConfiguration.getTiServiceConfig()));
    install(new STOServiceClientModule(stoManagerConfiguration.getStoServiceConfig()));
    install(new IACMServiceClientModule(stoManagerConfiguration.getIacmServiceConfig()));
    install(new AccountClientModule(stoManagerConfiguration.getManagerClientConfig(),
        stoManagerConfiguration.getNgManagerServiceSecret(), STO_MANAGER.toString()));
    install(EnforcementClientModule.getInstance(stoManagerConfiguration.getManagerClientConfig(),
        stoManagerConfiguration.getNgManagerServiceSecret(), STO_MANAGER.getServiceId(),
        stoManagerConfiguration.getEnforcementClientConfiguration()));
    install(new AbstractTelemetryModule() {
      @Override
      public TelemetryConfiguration telemetryConfiguration() {
        return stoManagerConfiguration.getSegmentConfiguration();
      }
    });
  }
}
