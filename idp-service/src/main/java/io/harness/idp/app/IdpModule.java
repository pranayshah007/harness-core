/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.app;

import static io.harness.authorization.AuthorizationServiceHeader.IDP_SERVICE;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.idp.provision.ProvisionConstants.PROVISION_MODULE_CONFIG;
import static io.harness.lock.DistributedLockImplementation.MONGO;

import io.harness.AccessControlClientModule;
import io.harness.account.AccountClientModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
import io.harness.client.NgConnectorManagerClientModule;
import io.harness.clients.BackstageResourceClientModule;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.exception.exceptionmanager.ExceptionModule;
import io.harness.git.GitClientV2;
import io.harness.git.GitClientV2Impl;
import io.harness.grpc.DelegateServiceDriverGrpcClientModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.idp.allowlist.resources.AllowListApiImpl;
import io.harness.idp.allowlist.services.AllowListService;
import io.harness.idp.allowlist.services.AllowListServiceImpl;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCache;
import io.harness.idp.common.delegateselectors.cache.memory.DelegateSelectorsInMemoryCache;
import io.harness.idp.configmanager.resource.AppConfigApiImpl;
import io.harness.idp.configmanager.resource.MergedPluginsConfigApiImpl;
import io.harness.idp.configmanager.service.*;
import io.harness.idp.envvariable.beans.entity.BackstageEnvConfigVariableEntity.BackstageEnvConfigVariableMapper;
import io.harness.idp.envvariable.beans.entity.BackstageEnvSecretVariableEntity.BackstageEnvSecretVariableMapper;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity.BackstageEnvVariableMapper;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableType;
import io.harness.idp.envvariable.resources.BackstageEnvVariableApiImpl;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.envvariable.service.BackstageEnvVariableServiceImpl;
import io.harness.idp.events.EventsFrameworkModule;
import io.harness.idp.events.eventlisteners.eventhandler.EntityCrudStreamListener;
import io.harness.idp.gitintegration.processor.factory.ConnectorProcessorFactory;
import io.harness.idp.gitintegration.resources.ConnectorInfoApiImpl;
import io.harness.idp.gitintegration.service.GitIntegrationService;
import io.harness.idp.gitintegration.service.GitIntegrationServiceImpl;
import io.harness.idp.health.resources.HealthResource;
import io.harness.idp.health.service.HealthResourceImpl;
import io.harness.idp.k8s.client.K8sApiClient;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.resource.AccountInfoApiImpl;
import io.harness.idp.namespace.resource.NamespaceApiImpl;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.namespace.service.NamespaceServiceImpl;
import io.harness.idp.onboarding.config.OnboardingModuleConfig;
import io.harness.idp.onboarding.resources.OnboardingResourceApiImpl;
import io.harness.idp.onboarding.service.OnboardingService;
import io.harness.idp.onboarding.service.impl.OnboardingServiceImpl;
import io.harness.idp.plugin.resources.AuthInfoApiImpl;
import io.harness.idp.plugin.resources.PluginInfoApiImpl;
import io.harness.idp.plugin.services.AuthInfoService;
import io.harness.idp.plugin.services.AuthInfoServiceImpl;
import io.harness.idp.plugin.services.PluginInfoService;
import io.harness.idp.plugin.services.PluginInfoServiceImpl;
import io.harness.idp.provision.ProvisionModuleConfig;
import io.harness.idp.provision.resource.ProvisionApiImpl;
import io.harness.idp.provision.service.ProvisionService;
import io.harness.idp.provision.service.ProvisionServiceImpl;
import io.harness.idp.proxy.delegate.DelegateProxyApi;
import io.harness.idp.proxy.delegate.DelegateProxyApiImpl;
import io.harness.idp.proxy.layout.LayoutProxyApiImpl;
import io.harness.idp.proxy.ngmanager.ManagerProxyApi;
import io.harness.idp.proxy.ngmanager.ManagerProxyApiImpl;
import io.harness.idp.proxy.ngmanager.NgManagerProxyApi;
import io.harness.idp.proxy.ngmanager.NgManagerProxyApiImpl;
import io.harness.idp.serializer.IdpServiceRegistrars;
import io.harness.idp.settings.resources.BackstagePermissionsApiImpl;
import io.harness.idp.settings.service.BackstagePermissionsService;
import io.harness.idp.settings.service.BackstagePermissionsServiceImpl;
import io.harness.idp.status.k8s.HealthCheck;
import io.harness.idp.status.k8s.PodHealthCheck;
import io.harness.idp.status.resources.StatusInfoApiImpl;
import io.harness.idp.status.service.StatusInfoService;
import io.harness.idp.status.service.StatusInfoServiceImpl;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.manage.ManagedExecutorService;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.metrics.modules.MetricsModule;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.event.MessageListener;
import io.harness.organization.OrganizationClientModule;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.project.ProjectClientModule;
import io.harness.queue.QueueController;
import io.harness.redis.RedisConfig;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.service.ServiceResourceClientModule;
import io.harness.spec.server.idp.v1.AccountInfoApi;
import io.harness.spec.server.idp.v1.AllowListApi;
import io.harness.spec.server.idp.v1.AppConfigApi;
import io.harness.spec.server.idp.v1.AuthInfoApi;
import io.harness.spec.server.idp.v1.BackstageEnvVariableApi;
import io.harness.spec.server.idp.v1.BackstagePermissionsApi;
import io.harness.spec.server.idp.v1.ConnectorInfoApi;
import io.harness.spec.server.idp.v1.LayoutProxyApi;
import io.harness.spec.server.idp.v1.MergedPluginsConfigApi;
import io.harness.spec.server.idp.v1.NamespaceApi;
import io.harness.spec.server.idp.v1.OnboardingResourceApi;
import io.harness.spec.server.idp.v1.PluginInfoApi;
import io.harness.spec.server.idp.v1.ProvisionApi;
import io.harness.spec.server.idp.v1.StatusInfoApi;
import io.harness.threading.ThreadPool;
import io.harness.time.TimeModule;
import io.harness.token.TokenClientModule;
import io.harness.version.VersionModule;
import io.harness.waiter.AbstractWaiterModule;
import io.harness.waiter.WaiterConfiguration;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IdpModule extends AbstractModule {
  private final IdpConfiguration appConfig;
  public IdpModule(IdpConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  protected void configure() {
    registerRequiredBindings();
    install(VersionModule.getInstance());
    install(new IdpPersistenceModule());
    install(IdpGrpcModule.getInstance());
    install(new AbstractMongoModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(IdpServiceRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(IdpServiceRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder().build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder().build();
      }

      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
      }

      @Provides
      @Singleton
      @Named("dbAliases")
      public List<String> getDbAliases() {
        return appConfig.getDbAliases();
      }

      @Provides
      @Singleton
      @Named("morphiaClasses")
      Map<Class, String> morphiaCustomCollectionNames() {
        return ImmutableMap.<Class, String>builder()
            .put(DelegateSyncTaskResponse.class, "idp_delegateSyncTaskResponses")
            .put(DelegateAsyncTaskResponse.class, "idp_delegateAsyncTaskResponses")
            .put(DelegateTaskProgressResponse.class, "idp_delegateTaskProgressResponses")
            .build();
      }
    });
    install(new MetricsModule());
    install(new EventsFrameworkModule(appConfig.getEventsFrameworkConfiguration()));
    install(new AbstractModule() {
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
    install(new SecretNGManagerClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new ConnectorResourceClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId(), ClientMode.PRIVILEGED));
    install(new TokenClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(AccessControlClientModule.getInstance(
        appConfig.getAccessControlClientConfiguration(), IDP_SERVICE.getServiceId()));
    install(
        new NgConnectorManagerClientModule(appConfig.getManagerClientConfig(), appConfig.getManagerServiceSecret()));
    install(new AccountClientModule(
        appConfig.getManagerClientConfig(), appConfig.getManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new OrganizationClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new ProjectClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new ServiceResourceClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new BackstageResourceClientModule());
    install(DelegateServiceDriverModule.getInstance(false, false));
    install(ExceptionModule.getInstance());
    install(new AbstractWaiterModule() {
      @Override
      public WaiterConfiguration waiterConfiguration() {
        return WaiterConfiguration.builder().persistenceLayer(WaiterConfiguration.PersistenceLayer.SPRING).build();
      }
    });
    install(new DelegateServiceDriverGrpcClientModule(
        appConfig.getManagerServiceSecret(), appConfig.getManagerTarget(), appConfig.getManagerAuthority(), true));

    bind(IdpConfiguration.class).toInstance(appConfig);
    install(PersistentLockModule.getInstance());
    install(TimeModule.getInstance());
    // Keeping it to 1 thread to start with. Assuming executor service is used only to
    // serve health checks. If it's being used for other tasks also, max pool size should be increased.
    bind(ExecutorService.class)
        .annotatedWith(Names.named("idpServiceExecutor"))
        .toInstance(ThreadPool.create(1, 2, 5, TimeUnit.SECONDS,
            new ThreadFactoryBuilder()
                .setNameFormat("default-idp-service-executor-%d")
                .setPriority(Thread.MIN_PRIORITY)
                .build()));
    bind(HPersistence.class).to(MongoPersistence.class).in(Singleton.class);
    bind(ConfigManagerService.class).to(ConfigManagerServiceImpl.class);
    bind(BackstageEnvVariableService.class).to(BackstageEnvVariableServiceImpl.class);
    bind(StatusInfoService.class).to(StatusInfoServiceImpl.class);
    bind(BackstagePermissionsService.class).to(BackstagePermissionsServiceImpl.class);
    bind(NamespaceService.class).to(NamespaceServiceImpl.class);
    bind(GitIntegrationService.class).to(GitIntegrationServiceImpl.class);
    bind(BackstageEnvVariableApi.class).to(BackstageEnvVariableApiImpl.class);
    bind(StatusInfoApi.class).to(StatusInfoApiImpl.class);
    bind(BackstagePermissionsApi.class).to(BackstagePermissionsApiImpl.class);
    bind(K8sClient.class).to(K8sApiClient.class);
    bind(HealthCheck.class).to(PodHealthCheck.class);
    bind(MessageListener.class).annotatedWith(Names.named(ENTITY_CRUD)).to(EntityCrudStreamListener.class);
    bind(ConnectorProcessorFactory.class);
    bind(NamespaceApi.class).to(NamespaceApiImpl.class);
    bind(AppConfigApi.class).to(AppConfigApiImpl.class);
    bind(AccountInfoApi.class).to(AccountInfoApiImpl.class);
    bind(ProvisionApi.class).to(ProvisionApiImpl.class);
    bind(ProvisionService.class).to(ProvisionServiceImpl.class);
    bind(OnboardingResourceApi.class).to(OnboardingResourceApiImpl.class);
    bind(OnboardingService.class).to(OnboardingServiceImpl.class);
    bind(GitClientV2.class).to(GitClientV2Impl.class);
    bind(LayoutProxyApi.class).to(LayoutProxyApiImpl.class);
    bind(NgManagerProxyApi.class).to(NgManagerProxyApiImpl.class);
    bind(ManagerProxyApi.class).to(ManagerProxyApiImpl.class);
    bind(PluginInfoApi.class).to(PluginInfoApiImpl.class);
    bind(DelegateProxyApi.class).to(DelegateProxyApiImpl.class);
    bind(PluginInfoService.class).to(PluginInfoServiceImpl.class);
    bind(ConnectorInfoApi.class).to(ConnectorInfoApiImpl.class);
    bind(MergedPluginsConfigApi.class).to(MergedPluginsConfigApiImpl.class);
    bind(ConfigEnvVariablesService.class).to(ConfigEnvVariablesServiceImpl.class);
    bind(AuthInfoApi.class).to(AuthInfoApiImpl.class);
    bind(AuthInfoService.class).to(AuthInfoServiceImpl.class);
    bind(AllowListApi.class).to(AllowListApiImpl.class);
    bind(AllowListService.class).to(AllowListServiceImpl.class);
    bind(PluginsProxyInfoService.class).to(PluginsProxyInfoServiceImpl.class);
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("backstageEnvVariableSyncer"))
        .toInstance(new ManagedScheduledExecutorService("backstageEnvVariableSyncer"));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("userSyncer"))
        .toInstance(new ManagedScheduledExecutorService("UserSyncer"));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("AppConfigPurger"))
        .toInstance(new ManagedScheduledExecutorService("AppConfigPurger"));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("DefaultPREnvAccountIdToNamespaceMappingCreator"))
        .toInstance(new ManagedExecutorService(Executors.newSingleThreadExecutor()));
    bind(HealthResource.class).to(HealthResourceImpl.class);
    bind(DelegateSelectorsCache.class).to(DelegateSelectorsInMemoryCache.class);

    MapBinder<BackstageEnvVariableType, BackstageEnvVariableMapper> backstageEnvVariableMapBinder =
        MapBinder.newMapBinder(binder(), BackstageEnvVariableType.class, BackstageEnvVariableMapper.class);
    backstageEnvVariableMapBinder.addBinding(BackstageEnvVariableType.CONFIG)
        .to(BackstageEnvConfigVariableMapper.class);
    backstageEnvVariableMapBinder.addBinding(BackstageEnvVariableType.SECRET)
        .to(BackstageEnvSecretVariableMapper.class);

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("taskPollExecutor"))
        .toInstance(new ManagedScheduledExecutorService("TaskPoll-Thread"));
  }

  @Provides
  @Singleton
  public MongoConfig mongoConfig() {
    return appConfig.getMongoConfig();
  }

  private void registerRequiredBindings() {
    requireBinding(HPersistence.class);
  }

  @Provides
  @Named("lock")
  @Singleton
  RedisConfig redisConfig() {
    return appConfig.getRedisLockConfig();
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return appConfig.getDistributedLockImplementation() == null ? MONGO : appConfig.getDistributedLockImplementation();
  }

  @Provides
  @Singleton
  @Named("onboardingModuleConfig")
  public OnboardingModuleConfig onboardingModuleConfig() {
    return this.appConfig.getOnboardingModuleConfig();
  }

  @Provides
  @Singleton
  @Named("backstageSaToken")
  public String backstageSaToken() {
    return this.appConfig.getBackstageSaToken();
  }

  @Provides
  @Singleton
  @Named("backstageSaCaCrt")
  public String backstageSaCaCrt() {
    return this.appConfig.getBackstageSaCaCrt();
  }

  @Provides
  @Singleton
  @Named("backstageMasterUrl")
  public String backstageMasterUrl() {
    return this.appConfig.getBackstageMasterUrl();
  }

  @Provides
  @Singleton
  @Named("backstagePodLabel")
  public String backstagePodLabel() {
    return this.appConfig.getBackstagePodLabel();
  }

  @Provides
  @Singleton
  @Named(PROVISION_MODULE_CONFIG)
  public ProvisionModuleConfig provisionModuleConfig() {
    return this.appConfig.getProvisionModuleConfig();
  }

  @Provides
  @Singleton
  @Named("backstageServiceSecret")
  public String backstageServiceSecret() {
    return this.appConfig.getBackstageServiceSecret();
  }

  @Provides
  @Singleton
  @Named("ngManagerServiceHttpClientConfig")
  public ServiceHttpClientConfig ngManagerServiceHttpClientConfig() {
    return this.appConfig.getNgManagerServiceHttpClientConfig();
  }
  @Provides
  @Singleton
  @Named("ngManagerServiceSecret")
  public String ngManagerServiceSecret() {
    return this.appConfig.getNgManagerServiceSecret();
  }

  @Provides
  @Singleton
  @Named("managerClientConfig")
  public ServiceHttpClientConfig managerClientConfig() {
    return this.appConfig.getManagerClientConfig();
  }

  @Provides
  @Singleton
  @Named("managerServiceSecret")
  public String managerServiceSecret() {
    return this.appConfig.getManagerServiceSecret();
  }

  @Provides
  @Singleton
  @Named("env")
  public String env() {
    return this.appConfig.getEnv();
  }

  @Provides
  @Singleton
  @Named("prEnvDefaultBackstageNamespace")
  public String prEnvDefaultBackstageNamespace() {
    return this.appConfig.getPrEnvDefaultBackstageNamespace();
  }

  @Provides
  @Singleton
  Supplier<DelegateCallbackToken> getDelegateCallbackTokenSupplier(
      DelegateServiceGrpcClient delegateServiceGrpcClient) {
    return Suppliers.memoize(() -> getDelegateCallbackToken(delegateServiceGrpcClient));
  }
  @Provides
  @Singleton
  @Named("backstageAppBaseUrl")
  public String appBaseUrl() {
    return this.appConfig.getBackstageAppBaseUrl();
  }
  @Provides
  @Singleton
  @Named("backstagePostgresHost")
  public String postgresHost() {
    return this.appConfig.getBackstagePostgresHost();
  }

  private DelegateCallbackToken getDelegateCallbackToken(DelegateServiceGrpcClient delegateServiceClient) {
    log.info("Generating Delegate callback token");
    final DelegateCallbackToken delegateCallbackToken = delegateServiceClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix("idp")
                                  .setConnection(appConfig.getMongoConfig().getUri())
                                  .build())
            .build());
    log.info("delegate callback token generated =[{}]", delegateCallbackToken.getToken());
    return delegateCallbackToken;
  }

  @Provides
  @Singleton
  @Named("backstageHttpClientConfig")
  public ServiceHttpClientConfig backstageHttpClientConfig() {
    return this.appConfig.getBackstageHttpClientConfig();
  }
}
