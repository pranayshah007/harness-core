/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import static io.harness.AuthorizationServiceHeader.STO_MANAGER;
import static io.harness.lock.DistributedLockImplementation.MONGO;

import io.harness.AccessControlClientModule;
import io.harness.CIExecutionServiceModule;
import io.harness.account.AccountClientModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.impl.STOYamlSchemaServiceImpl;
import io.harness.app.intfc.STOYamlSchemaService;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
import io.harness.concurrent.HTimeLimiter;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.entitysetupusageclient.EntitySetupUsageClientModule;
import io.harness.ff.CIFeatureFlagService;
import io.harness.ff.impl.CIFeatureFlagServiceImpl;
import io.harness.grpc.DelegateServiceDriverGrpcClientModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.grpc.client.AbstractManagerGrpcClientModule;
import io.harness.grpc.client.ManagerGrpcClientModule;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.logserviceclient.CILogServiceClientModule;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.mongo.MongoPersistence;
import io.harness.opaclient.OpaClientModule;
import io.harness.packages.HarnessPackages;
import io.harness.persistence.HPersistence;
import io.harness.redis.RedisConfig;
import io.harness.remote.client.ClientMode;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.stoserviceclient.STOServiceClientModule;
import io.harness.telemetry.AbstractTelemetryModule;
import io.harness.telemetry.TelemetryConfiguration;
import io.harness.threading.ThreadPool;
import io.harness.tiserviceclient.TIServiceClientModule;
import io.harness.token.TokenClientModule;
import io.harness.user.UserClientModule;
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
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

@Slf4j
@OwnedBy(HarnessTeam.STO)
public class STOManagerServiceModule extends AbstractModule {
  private final STOManagerConfiguration stoManagerConfiguration;

  public STOManagerServiceModule(STOManagerConfiguration stoManagerConfiguration) {
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

  private DelegateCallbackToken getDelegateCallbackToken(
      DelegateServiceGrpcClient delegateServiceClient, STOManagerConfiguration appConfig) {
    log.info("Generating Delegate callback token");
    final DelegateCallbackToken delegateCallbackToken = delegateServiceClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix("stoManager")
                                  .setConnection(appConfig.getHarnessSTOMongo().getUri())
                                  .build())
            .build());
    log.info("Delegate callback token generated =[{}]", delegateCallbackToken.getToken());
    return delegateCallbackToken;
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
    install(PrimaryVersionManagerModule.getInstance());
    bind(STOManagerConfiguration.class).toInstance(stoManagerConfiguration);
    bind(HPersistence.class).to(MongoPersistence.class).in(Singleton.class);
    bind(STOYamlSchemaService.class).to(STOYamlSchemaServiceImpl.class).in(Singleton.class);
    bind(CIFeatureFlagService.class).to(CIFeatureFlagServiceImpl.class).in(Singleton.class);

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

    install(new CIExecutionServiceModule(
        stoManagerConfiguration.getCiExecutionServiceConfig(), stoManagerConfiguration.getShouldConfigureWithPMS()));
    install(DelegateServiceDriverModule.getInstance(false, true));
    install(new DelegateServiceDriverGrpcClientModule(stoManagerConfiguration.getManagerServiceSecret(),
        stoManagerConfiguration.getManagerTarget(), stoManagerConfiguration.getManagerAuthority(), true));

    install(new TokenClientModule(stoManagerConfiguration.getNgManagerClientConfig(),
        stoManagerConfiguration.getNgManagerServiceSecret(), STO_MANAGER.getServiceId()));
    install(PersistentLockModule.getInstance());

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
    install(new OpaClientModule(
        stoManagerConfiguration.getOpaServerConfig().getBaseUrl(), stoManagerConfiguration.getJwtAuthSecret()));
    install(UserClientModule.getInstance(stoManagerConfiguration.getManagerClientConfig(),
        stoManagerConfiguration.getManagerServiceSecret(), STO_MANAGER.getServiceId()));
    install(new TIServiceClientModule(stoManagerConfiguration.getTiServiceConfig()));
    install(new STOServiceClientModule(stoManagerConfiguration.getStoServiceConfig()));
    install(new AccountClientModule(stoManagerConfiguration.getManagerClientConfig(),
        stoManagerConfiguration.getNgManagerServiceSecret(), STO_MANAGER.toString()));
    install(new AbstractTelemetryModule() {
      @Override
      public TelemetryConfiguration telemetryConfiguration() {
        return stoManagerConfiguration.getSegmentConfiguration();
      }
    });
  }
}
