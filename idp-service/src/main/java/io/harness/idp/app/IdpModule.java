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

import io.harness.AccessControlClientModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.client.NgConnectorManagerClientModule;
import io.harness.clients.BackstageCatalogResourceClientModule;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.git.GitClientV2;
import io.harness.git.GitClientV2Impl;
import io.harness.idp.config.service.AppConfigService;
import io.harness.idp.config.service.AppConfigServiceImpl;
import io.harness.idp.events.EventsFrameworkModule;
import io.harness.idp.events.eventlisteners.eventhandler.EntityCrudStreamListener;
import io.harness.idp.gitintegration.factory.ConnectorProcessorFactory;
import io.harness.idp.gitintegration.service.GitIntegrationService;
import io.harness.idp.gitintegration.service.GitIntegrationServiceImpl;
import io.harness.idp.k8s.client.K8sApiClient;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.resource.AccountInfoApiImpl;
import io.harness.idp.namespace.resource.NamespaceApiImpl;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.namespace.service.NamespaceServiceImpl;
import io.harness.idp.onboarding.OnboardingModuleConfig;
import io.harness.idp.onboarding.resources.OnboardingResourceApiImpl;
import io.harness.idp.onboarding.services.OnboardingService;
import io.harness.idp.onboarding.services.impl.OnboardingServiceImpl;
import io.harness.idp.provision.ProvisionModuleConfig;
import io.harness.idp.provision.resource.ProvisionApiImpl;
import io.harness.idp.provision.service.ProvisionService;
import io.harness.idp.provision.service.ProvisionServiceImpl;
import io.harness.idp.secret.resources.EnvironmentSecretApiImpl;
import io.harness.idp.secret.service.EnvironmentSecretService;
import io.harness.idp.secret.service.EnvironmentSecretServiceImpl;
import io.harness.idp.serializer.IdpServiceRegistrars;
import io.harness.idp.settings.resources.BackstagePermissionsApiImpl;
import io.harness.idp.settings.service.BackstagePermissionsService;
import io.harness.idp.settings.service.BackstagePermissionsServiceImpl;
import io.harness.idp.status.k8s.HealthCheck;
import io.harness.idp.status.k8s.PodHealthCheck;
import io.harness.idp.status.resources.StatusInfoApiImpl;
import io.harness.idp.status.service.StatusInfoService;
import io.harness.idp.status.service.StatusInfoServiceImpl;
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
import io.harness.remote.client.ClientMode;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.service.ServiceResourceClientModule;
import io.harness.spec.server.idp.v1.AccountInfoApi;
import io.harness.spec.server.idp.v1.BackstagePermissionsApi;
import io.harness.spec.server.idp.v1.EnvironmentSecretApi;
import io.harness.spec.server.idp.v1.NamespaceApi;
import io.harness.spec.server.idp.v1.OnboardingResourceApi;
import io.harness.spec.server.idp.v1.ProvisionApi;
import io.harness.spec.server.idp.v1.StatusInfoApi;
import io.harness.threading.ThreadPool;
import io.harness.token.TokenClientModule;
import io.harness.version.VersionModule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
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
        return ImmutableMap.<Class, String>builder().build();
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
    install(new OrganizationClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new ProjectClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new ServiceResourceClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new BackstageCatalogResourceClientModule(
        appConfig.getBackstageHttpClientConfig(), appConfig.getBackstageServiceSecret(), IDP_SERVICE.getServiceId()));

    bind(IdpConfiguration.class).toInstance(appConfig);
    // Keeping it to 1 thread to start with. Assuming executor service is used only to
    // serve health checks. If it's being used for other tasks also, max pool size should be increased.
    bind(ExecutorService.class)
        .toInstance(ThreadPool.create(1, 2, 5, TimeUnit.SECONDS,
            new ThreadFactoryBuilder()
                .setNameFormat("default-idp-service-executor-%d")
                .setPriority(Thread.MIN_PRIORITY)
                .build()));
    bind(HPersistence.class).to(MongoPersistence.class).in(Singleton.class);
    bind(AppConfigService.class).to(AppConfigServiceImpl.class);
    bind(EnvironmentSecretService.class).to(EnvironmentSecretServiceImpl.class);
    bind(StatusInfoService.class).to(StatusInfoServiceImpl.class);
    bind(BackstagePermissionsService.class).to(BackstagePermissionsServiceImpl.class);
    bind(NamespaceService.class).to(NamespaceServiceImpl.class);
    bind(GitIntegrationService.class).to(GitIntegrationServiceImpl.class);
    bind(EnvironmentSecretApi.class).to(EnvironmentSecretApiImpl.class);
    bind(StatusInfoApi.class).to(StatusInfoApiImpl.class);
    bind(BackstagePermissionsApi.class).to(BackstagePermissionsApiImpl.class);
    bind(K8sClient.class).to(K8sApiClient.class);
    bind(HealthCheck.class).to(PodHealthCheck.class);
    bind(MessageListener.class).annotatedWith(Names.named(ENTITY_CRUD)).to(EntityCrudStreamListener.class);
    bind(ConnectorProcessorFactory.class);
    bind(NamespaceApi.class).to(NamespaceApiImpl.class);
    bind(AccountInfoApi.class).to(AccountInfoApiImpl.class);
    bind(ProvisionApi.class).to(ProvisionApiImpl.class);
    bind(ProvisionService.class).to(ProvisionServiceImpl.class);
    bind(OnboardingResourceApi.class).to(OnboardingResourceApiImpl.class);
    bind(OnboardingService.class).to(OnboardingServiceImpl.class);
    bind(GitClientV2.class).to(GitClientV2Impl.class);
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
  @Named(PROVISION_MODULE_CONFIG)
  public ProvisionModuleConfig provisionModuleConfig() {
    return this.appConfig.getProvisionModuleConfig();
  }
}
