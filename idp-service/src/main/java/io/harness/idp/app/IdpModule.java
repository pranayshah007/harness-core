/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.app;

import static io.harness.audit.ResourceTypeConstants.IDP_ALLOW_LIST;
import static io.harness.audit.ResourceTypeConstants.IDP_APP_CONFIGS;
import static io.harness.audit.ResourceTypeConstants.IDP_CATALOG_CONNECTOR;
import static io.harness.audit.ResourceTypeConstants.IDP_CHECKS;
import static io.harness.audit.ResourceTypeConstants.IDP_CONFIG_ENV_VARIABLES;
import static io.harness.audit.ResourceTypeConstants.IDP_OAUTH_CONFIG;
import static io.harness.audit.ResourceTypeConstants.IDP_PROXY_HOST;
import static io.harness.audit.ResourceTypeConstants.IDP_SCORECARDS;
import static io.harness.authorization.AuthorizationServiceHeader.IDP_SERVICE;
import static io.harness.ci.execution.utils.HostedVmSecretResolver.SECRET_CACHE_KEY;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.idp.provision.ProvisionConstants.PROVISION_MODULE_CONFIG;
import static io.harness.lock.DistributedLockImplementation.REDIS;
import static io.harness.outbox.OutboxSDKConstants.DEFAULT_OUTBOX_POLL_CONFIGURATION;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;

import io.harness.AccessControlClientModule;
import io.harness.ScmConnectionConfig;
import io.harness.account.AccountClientModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.client.remote.AuditClientModule;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsClientImpl;
import io.harness.beans.entities.IACMServiceConfig;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.cache.NoOpCache;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
import io.harness.ci.CIExecutionServiceModule;
import io.harness.ci.beans.entities.EncryptedDataDetails;
import io.harness.ci.beans.entities.LogServiceConfig;
import io.harness.ci.beans.entities.TIServiceConfig;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.enforcement.CIBuildEnforcer;
import io.harness.ci.execution.buildstate.PluginSettingUtils;
import io.harness.ci.execution.buildstate.SecretDecryptorViaNg;
import io.harness.ci.execution.serializer.PluginCompatibleStepSerializer;
import io.harness.ci.execution.validation.CIAccountValidationService;
import io.harness.ci.execution.validation.CIAccountValidationServiceImpl;
import io.harness.ci.execution.validation.CIYAMLSanitizationService;
import io.harness.ci.execution.validation.CIYAMLSanitizationServiceImpl;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.ff.impl.CIFeatureFlagServiceImpl;
import io.harness.ci.license.impl.CILicenseServiceImpl;
import io.harness.ci.logserviceclient.CILogServiceClientModule;
import io.harness.ci.tiserviceclient.TIServiceClientModule;
import io.harness.cistatus.service.GithubService;
import io.harness.cistatus.service.GithubServiceImpl;
import io.harness.cistatus.service.azurerepo.AzureRepoService;
import io.harness.cistatus.service.azurerepo.AzureRepoServiceImpl;
import io.harness.cistatus.service.bitbucket.BitbucketService;
import io.harness.cistatus.service.bitbucket.BitbucketServiceImpl;
import io.harness.cistatus.service.gitlab.GitlabService;
import io.harness.cistatus.service.gitlab.GitlabServiceImpl;
import io.harness.client.NgConnectorManagerClientModule;
import io.harness.clients.BackstageResourceClientModule;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.creditcard.CreditCardClientModule;
import io.harness.dashboard.DashboardResourceClientModule;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.enforcement.client.EnforcementClientConfiguration;
import io.harness.enforcement.client.EnforcementClientModule;
import io.harness.entitysetupusageclient.EntitySetupUsageClientModule;
import io.harness.exception.exceptionmanager.ExceptionModule;
import io.harness.git.GitClientV2;
import io.harness.git.GitClientV2Impl;
import io.harness.grpc.DelegateServiceDriverGrpcClientModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.iacmserviceclient.IACMServiceClientModule;
import io.harness.idp.allowlist.resources.AllowListApiImpl;
import io.harness.idp.allowlist.services.AllowListService;
import io.harness.idp.allowlist.services.AllowListServiceImpl;
import io.harness.idp.audittrails.eventhandlers.AllowListEventHandler;
import io.harness.idp.audittrails.eventhandlers.AppConfigEventHandler;
import io.harness.idp.audittrails.eventhandlers.BackstageSecretEnvEventHandler;
import io.harness.idp.audittrails.eventhandlers.CatalogConnectorEventHandler;
import io.harness.idp.audittrails.eventhandlers.CheckEventHandler;
import io.harness.idp.audittrails.eventhandlers.IDPNextGenOutboxEventHandler;
import io.harness.idp.audittrails.eventhandlers.OAuthConfigEventHandler;
import io.harness.idp.audittrails.eventhandlers.ProxyHostDetailsEventHandler;
import io.harness.idp.audittrails.eventhandlers.ScorecardEventHandler;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCache;
import io.harness.idp.common.delegateselectors.cache.memory.DelegateSelectorsInMemoryCache;
import io.harness.idp.common.delegateselectors.cache.redis.DelegateSelectorsRedisCache;
import io.harness.idp.configmanager.resource.AppConfigApiImpl;
import io.harness.idp.configmanager.resource.MergedPluginsConfigApiImpl;
import io.harness.idp.configmanager.service.ConfigEnvVariablesService;
import io.harness.idp.configmanager.service.ConfigEnvVariablesServiceImpl;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.service.ConfigManagerServiceImpl;
import io.harness.idp.configmanager.service.PluginsProxyInfoService;
import io.harness.idp.configmanager.service.PluginsProxyInfoServiceImpl;
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
import io.harness.idp.license.usage.resources.LicenseUsageResourceApiImpl;
import io.harness.idp.license.usage.service.IDPModuleLicenseUsage;
import io.harness.idp.license.usage.service.impl.IDPLicenseUsageImpl;
import io.harness.idp.license.usage.service.impl.IDPModuleLicenseUsageImpl;
import io.harness.idp.namespace.resource.AccountInfoApiImpl;
import io.harness.idp.namespace.resource.NamespaceApiImpl;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.namespace.service.NamespaceServiceImpl;
import io.harness.idp.onboarding.config.OnboardingModuleConfig;
import io.harness.idp.onboarding.resources.OnboardingResourceApiImpl;
import io.harness.idp.onboarding.service.OnboardingService;
import io.harness.idp.onboarding.service.impl.OnboardingServiceImpl;
import io.harness.idp.pipeline.IDPBuildEnforcerImpl;
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
import io.harness.idp.proxy.config.ProxyAllowListConfig;
import io.harness.idp.proxy.delegate.DelegateProxyApi;
import io.harness.idp.proxy.delegate.DelegateProxyApiImpl;
import io.harness.idp.proxy.layout.LayoutProxyApiImpl;
import io.harness.idp.proxy.services.ProxyApi;
import io.harness.idp.proxy.services.ProxyApiImpl;
import io.harness.idp.scorecard.checks.resources.ChecksApiImpl;
import io.harness.idp.scorecard.checks.service.CheckService;
import io.harness.idp.scorecard.checks.service.CheckServiceImpl;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datapoints.service.DataPointServiceImpl;
import io.harness.idp.scorecard.datapointsdata.resource.HarnessDataPointsApiImpl;
import io.harness.idp.scorecard.datapointsdata.resource.KubernetesDataPointsApiImpl;
import io.harness.idp.scorecard.datapointsdata.service.DataPointDataValueService;
import io.harness.idp.scorecard.datapointsdata.service.DataPointDataValueServiceImpl;
import io.harness.idp.scorecard.datapointsdata.service.KubernetesDataPointsService;
import io.harness.idp.scorecard.datapointsdata.service.KubernetesDataPointsServiceImpl;
import io.harness.idp.scorecard.datasourcelocations.service.DataSourceLocationService;
import io.harness.idp.scorecard.datasourcelocations.service.DataSourceLocationServiceImpl;
import io.harness.idp.scorecard.datasources.resources.DataSourceApiImpl;
import io.harness.idp.scorecard.datasources.service.DataSourceService;
import io.harness.idp.scorecard.datasources.service.DataSourceServiceImpl;
import io.harness.idp.scorecard.scorecards.resources.ScorecardsApiImpl;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scorecards.service.ScorecardServiceImpl;
import io.harness.idp.scorecard.scores.resources.ScoreApiImpl;
import io.harness.idp.scorecard.scores.service.ScoreComputerService;
import io.harness.idp.scorecard.scores.service.ScoreComputerServiceImpl;
import io.harness.idp.scorecard.scores.service.ScoreService;
import io.harness.idp.scorecard.scores.service.ScoreServiceImpl;
import io.harness.idp.serializer.IdpServiceRegistrars;
import io.harness.idp.settings.resources.BackstagePermissionsApiImpl;
import io.harness.idp.settings.service.BackstagePermissionsService;
import io.harness.idp.settings.service.BackstagePermissionsServiceImpl;
import io.harness.idp.status.k8s.HealthCheck;
import io.harness.idp.status.k8s.PodHealthCheck;
import io.harness.idp.status.resources.StatusInfoApiImpl;
import io.harness.idp.status.resources.StatusInfoV2ApiImpl;
import io.harness.idp.status.service.StatusInfoService;
import io.harness.idp.status.service.StatusInfoServiceImpl;
import io.harness.impl.scm.ScmServiceClientImpl;
import io.harness.licensing.remote.NgLicenseHttpClientModule;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.manage.ManagedExecutorService;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.mongo.iterator.IteratorConfig;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.entitysetupusage.EntitySetupUsageModule;
import io.harness.ng.core.event.MessageListener;
import io.harness.ngsettings.client.remote.NGSettingsClientModule;
import io.harness.opaclient.OpaClientModule;
import io.harness.organization.OrganizationClientModule;
import io.harness.outbox.TransactionOutboxModule;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.packages.HarnessPackages;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.pipeline.dashboards.PMSDashboardResourceClientModule;
import io.harness.pipeline.remote.PipelineRemoteClientModule;
import io.harness.plugin.service.BasePluginCompatibleSerializer;
import io.harness.plugin.service.PluginService;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.project.ProjectClientModule;
import io.harness.queue.QueueController;
import io.harness.redis.RedisConfig;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.secrets.SecretDecryptor;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.service.ScmServiceClient;
import io.harness.service.ServiceResourceClientModule;
import io.harness.spec.server.idp.v1.AccountInfoApi;
import io.harness.spec.server.idp.v1.AllowListApi;
import io.harness.spec.server.idp.v1.AppConfigApi;
import io.harness.spec.server.idp.v1.AuthInfoApi;
import io.harness.spec.server.idp.v1.BackstageEnvVariableApi;
import io.harness.spec.server.idp.v1.BackstagePermissionsApi;
import io.harness.spec.server.idp.v1.ChecksApi;
import io.harness.spec.server.idp.v1.ConnectorInfoApi;
import io.harness.spec.server.idp.v1.DataSourceApi;
import io.harness.spec.server.idp.v1.HarnessDataPointsApi;
import io.harness.spec.server.idp.v1.KubernetesDataPointsApi;
import io.harness.spec.server.idp.v1.LayoutProxyApi;
import io.harness.spec.server.idp.v1.LicenseUsageResourceApi;
import io.harness.spec.server.idp.v1.MergedPluginsConfigApi;
import io.harness.spec.server.idp.v1.NamespaceApi;
import io.harness.spec.server.idp.v1.OnboardingResourceApi;
import io.harness.spec.server.idp.v1.PluginInfoApi;
import io.harness.spec.server.idp.v1.ProvisionApi;
import io.harness.spec.server.idp.v1.ScorecardsApi;
import io.harness.spec.server.idp.v1.ScoresApi;
import io.harness.spec.server.idp.v1.StatusInfoApi;
import io.harness.spec.server.idp.v1.StatusInfoV2Api;
import io.harness.ssca.beans.entities.SSCAServiceConfig;
import io.harness.ssca.client.SSCAServiceClientModuleV2;
import io.harness.sto.beans.entities.STOServiceConfig;
import io.harness.stoserviceclient.STOServiceClientModule;
import io.harness.telemetry.AbstractTelemetryModule;
import io.harness.telemetry.TelemetryConfiguration;
import io.harness.telemetry.segment.SegmentConfiguration;
import io.harness.threading.ThreadPool;
import io.harness.threading.ThreadPoolConfig;
import io.harness.time.TimeModule;
import io.harness.token.TokenClientModule;
import io.harness.user.UserClientModule;
import io.harness.version.VersionModule;
import io.harness.waiter.AsyncWaitEngineImpl;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.yaml.core.StepSpecType;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.dropwizard.jackson.Jackson;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.springframework.core.convert.converter.Converter;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IdpModule extends AbstractModule {
  public static final String REDIS_CACHE = "redis";
  public static final String IN_MEMORY = "in-memory";
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
    install(new DashboardResourceClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId(), ClientMode.PRIVILEGED));
    install(new TIServiceClientModule(appConfig.getTiServiceConfig()));
    install(new ConnectorResourceClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId(), ClientMode.PRIVILEGED));
    install(new PMSDashboardResourceClientModule(appConfig.getPipelineServiceConfiguration(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
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
    install(new NGSettingsClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new PipelineRemoteClientModule(
        appConfig.getPipelineServiceConfiguration(), appConfig.getPipelineServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new TransactionOutboxModule(DEFAULT_OUTBOX_POLL_CONFIGURATION, IDP_SERVICE.getServiceId(), false));
    install(new BackstageResourceClientModule());
    install(DelegateServiceDriverModule.getInstance(false, false));
    install(ExceptionModule.getInstance());
    //    install(new AbstractWaiterModule() {
    //      @Override
    //      public WaiterConfiguration waiterConfiguration() {
    //        return
    //        WaiterConfiguration.builder().persistenceLayer(WaiterConfiguration.PersistenceLayer.SPRING).build();
    //      }
    //    });
    install(new DelegateServiceDriverGrpcClientModule(
        appConfig.getManagerServiceSecret(), appConfig.getManagerTarget(), appConfig.getManagerAuthority(), true));
    install(new AuditClientModule(appConfig.getAuditClientConfig(), appConfig.getNgManagerServiceSecret(),
        IDP_SERVICE.getServiceId(), appConfig.isEnableAudit()));
    install(new CreditCardClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));

    bind(IdpConfiguration.class).toInstance(appConfig);
    install(PersistentLockModule.getInstance());
    install(TimeModule.getInstance());
    install(new CILogServiceClientModule(appConfig.getLogServiceConfig()));
    install(NgLicenseHttpClientModule.getInstance(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new SSCAServiceClientModuleV2(appConfig.getSscaServiceConfig(), IDP_SERVICE.getServiceId()));
    install(new STOServiceClientModule(appConfig.getStoServiceConfig()));
    install(UserClientModule.getInstance(
        appConfig.getManagerClientConfig(), appConfig.getManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new OpaClientModule(
        appConfig.getOpaClientConfig(), appConfig.getPolicyManagerSecret(), IDP_SERVICE.getServiceId()));
    install(
        new CIExecutionServiceModule(appConfig.getCiExecutionServiceConfig(), appConfig.getShouldConfigureWithPMS()));
    install(new IACMServiceClientModule(appConfig.getIacmServiceConfig()));
    install(EnforcementClientModule.getInstance(appConfig.getManagerClientConfig(), // Licencing
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId(),
        appConfig.getEnforcementClientConfiguration()));
    // Keeping it to 1 thread to start with. Assuming executor service is used only to
    // serve health checks. If it's being used for other tasks also, max pool size should be increased.
    bind(ExecutorService.class)
        .annotatedWith(Names.named("idpServiceExecutor"))
        .toInstance(ThreadPool.create(1, 2, 5, TimeUnit.SECONDS,
            new ThreadFactoryBuilder()
                .setNameFormat("default-idp-service-executor-%d")
                .setPriority(Thread.MIN_PRIORITY)
                .build()));

    registerOutboxEventHandlers();
    bind(OutboxEventHandler.class).to(IDPNextGenOutboxEventHandler.class);
    bind(HPersistence.class).to(MongoPersistence.class).in(Singleton.class);
    bind(ConfigManagerService.class).to(ConfigManagerServiceImpl.class);
    bind(BackstageEnvVariableService.class).to(BackstageEnvVariableServiceImpl.class);
    bind(StatusInfoService.class).to(StatusInfoServiceImpl.class);
    bind(BackstagePermissionsService.class).to(BackstagePermissionsServiceImpl.class);
    bind(NamespaceService.class).to(NamespaceServiceImpl.class);
    bind(GitIntegrationService.class).to(GitIntegrationServiceImpl.class);
    bind(BackstageEnvVariableApi.class).to(BackstageEnvVariableApiImpl.class);
    bind(StatusInfoApi.class).to(StatusInfoApiImpl.class);
    bind(StatusInfoV2Api.class).to(StatusInfoV2ApiImpl.class);
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
    bind(GithubService.class).to(GithubServiceImpl.class);
    bind(LayoutProxyApi.class).to(LayoutProxyApiImpl.class);
    bind(ProxyApi.class).to(ProxyApiImpl.class);
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
    bind(ScorecardsApi.class).to(ScorecardsApiImpl.class);
    bind(ScorecardService.class).to(ScorecardServiceImpl.class);
    bind(ChecksApi.class).to(ChecksApiImpl.class);
    bind(CheckService.class).to(CheckServiceImpl.class);
    bind(ScoresApi.class).to(ScoreApiImpl.class);
    bind(DataSourceApi.class).to(DataSourceApiImpl.class);
    bind(DataSourceService.class).to(DataSourceServiceImpl.class);
    bind(DataPointService.class).to(DataPointServiceImpl.class);
    bind(DataSourceLocationService.class).to(DataSourceLocationServiceImpl.class);
    bind(ScoreService.class).to(ScoreServiceImpl.class);
    bind(ScoreComputerService.class).to(ScoreComputerServiceImpl.class);
    bind(DataPointService.class).to(DataPointServiceImpl.class);
    bind(HarnessDataPointsApi.class).to(HarnessDataPointsApiImpl.class);
    bind(KubernetesDataPointsApi.class).to(KubernetesDataPointsApiImpl.class);
    bind(DataPointDataValueService.class).to(DataPointDataValueServiceImpl.class);
    bind(KubernetesDataPointsService.class).to(KubernetesDataPointsServiceImpl.class);
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
    bind(ExecutorService.class)
        .annotatedWith(Names.named("ScoreComputer"))
        .toInstance(new ManagedExecutorService(Executors.newFixedThreadPool(
            Integer.parseInt(appConfig.getCpu()) * Integer.parseInt(appConfig.getScoreComputerThreadsPerCore()),
            new ThreadFactoryBuilder().setNameFormat("score-computer-%d").build())));
    bind(HealthResource.class).to(HealthResourceImpl.class);
    bind(CILicenseService.class).to(CILicenseServiceImpl.class).in(Singleton.class);
    bind(CIFeatureFlagService.class).to(CIFeatureFlagServiceImpl.class).in(Singleton.class);
    bind(CIAccountValidationService.class).to(CIAccountValidationServiceImpl.class).in(Singleton.class);
    bind(SecretDecryptor.class).to(SecretDecryptorViaNg.class); // same?
    bind(AwsClient.class).to(AwsClientImpl.class);
    bind(GithubService.class).to(GithubServiceImpl.class);
    bind(AzureRepoService.class).to(AzureRepoServiceImpl.class);
    bind(BitbucketService.class).to(BitbucketServiceImpl.class);
    bind(GitlabService.class).to(GitlabServiceImpl.class);
    bind(ScmServiceClient.class).to(ScmServiceClientImpl.class);
    bind(CIBuildEnforcer.class).to(IDPBuildEnforcerImpl.class);

    if (appConfig.getDelegateSelectorsCacheMode().equals(IN_MEMORY)) {
      bind(DelegateSelectorsCache.class).to(DelegateSelectorsInMemoryCache.class);
    } else if (appConfig.getDelegateSelectorsCacheMode().equals(REDIS_CACHE)) {
      bind(DelegateSelectorsCache.class).to(DelegateSelectorsRedisCache.class);
    }

    MapBinder<BackstageEnvVariableType, BackstageEnvVariableMapper> backstageEnvVariableMapBinder =
        MapBinder.newMapBinder(binder(), BackstageEnvVariableType.class, BackstageEnvVariableMapper.class);
    backstageEnvVariableMapBinder.addBinding(BackstageEnvVariableType.CONFIG)
        .to(BackstageEnvConfigVariableMapper.class);
    backstageEnvVariableMapBinder.addBinding(BackstageEnvVariableType.SECRET)
        .to(BackstageEnvSecretVariableMapper.class);

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("taskPollExecutor"))
        .toInstance(new ManagedScheduledExecutorService("TaskPoll-Thread"));

    bind(PluginService.class).to(PluginSettingUtils.class);
    bind(BasePluginCompatibleSerializer.class).to(PluginCompatibleStepSerializer.class);
    bind(CIYAMLSanitizationService.class).to(CIYAMLSanitizationServiceImpl.class).in(Singleton.class);
    //    bind(HsqsClient.class).toProvider(HsqsServiceHttpClientFactory.class).in(Scopes.SINGLETON);

    bind(LicenseUsageInterface.class).to(IDPLicenseUsageImpl.class);
    bind(IDPModuleLicenseUsage.class).to(IDPModuleLicenseUsageImpl.class);
    bind(LicenseUsageResourceApi.class).to(LicenseUsageResourceApiImpl.class);
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("licenseUsageDailyCountJob"))
        .toInstance(new ManagedScheduledExecutorService("licenseUsageDailyCountJob"));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("checkStatusDailyRunJob"))
        .toInstance(new ManagedScheduledExecutorService("checkStatusDailyRunJob"));
    install(new AbstractTelemetryModule() {
      @Override
      public TelemetryConfiguration telemetryConfiguration() {
        return appConfig.getSegmentConfiguration();
      }
    });
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("idpTelemetryPublisherExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder()
                .setNameFormat("idp-telemetry-publisher-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));
  }

  private void registerOutboxEventHandlers() {
    MapBinder<String, OutboxEventHandler> outboxEventHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, OutboxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_APP_CONFIGS).to(AppConfigEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_CONFIG_ENV_VARIABLES).to(BackstageSecretEnvEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_PROXY_HOST).to(ProxyHostDetailsEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_CATALOG_CONNECTOR).to(CatalogConnectorEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_SCORECARDS).to(ScorecardEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_CHECKS).to(CheckEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_ALLOW_LIST).to(AllowListEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_OAUTH_CONFIG).to(OAuthConfigEventHandler.class);
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
    return appConfig.getDistributedLockImplementation() == null ? REDIS : appConfig.getDistributedLockImplementation();
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
  @Named("backstageEntitiesFetchLimit")
  public String backstageEntitiesFetchLimit() {
    return this.appConfig.getBackstageEntitiesFetchLimit();
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

  @Provides
  @Singleton
  @Named("idpEncryptionSecret")
  public String idpEncryptionSecret() {
    return this.appConfig.getIdpEncryptionSecret();
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
  @Named("logServiceConfig")
  public LogServiceConfig logServiceConfig() {
    return this.appConfig.getLogServiceConfig();
  }

  @Provides
  @Singleton
  @Named("sscaServiceConfig")
  public SSCAServiceConfig sscaServiceConfig() {
    return this.appConfig.getSscaServiceConfig();
  }

  @Provides
  @Singleton
  @Named("stoServiceConfig")
  public STOServiceConfig stoServiceConfig() {
    return this.appConfig.getStoServiceConfig();
  }

  @Provides
  @Singleton
  @Named("ngBaseUrl")
  String getNgBaseUrl() {
    String apiUrl = appConfig.getApiUrl();
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

  @Provides
  @Named("yaml-schema-mapper")
  @Singleton
  public ObjectMapper getYamlSchemaObjectMapper() {
    ObjectMapper objectMapper = Jackson.newObjectMapper();
    IdpApplication.configureObjectMapper(objectMapper);
    return objectMapper;
  }
  @Provides
  @Singleton
  List<YamlSchemaRootClass> yamlSchemaRootClasses() {
    return ImmutableList.<YamlSchemaRootClass>builder().addAll(IdpServiceRegistrars.yamlSchemaRegistrars).build();
  }

  @Provides
  @Singleton
  ScmConnectionConfig scmConnectionConfig() {
    return this.appConfig.getScmConnectionConfig();
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
  @Named("pmsSdkExecutionPoolConfig")
  public ThreadPoolConfig pmsSdkExecutionPoolConfig() {
    return this.appConfig.getPmsSdkExecutionPoolConfig();
  }

  @Provides
  @Singleton
  @Named("pmsSdkOrchestrationEventPoolConfig")
  public ThreadPoolConfig pmsSdkOrchestrationEventPoolConfig() {
    return this.appConfig.getPmsSdkOrchestrationEventPoolConfig();
  }

  @Provides
  @Singleton
  @Named("pmsPlanCreatorServicePoolConfig")
  public ThreadPoolConfig pmsPlanCreatorServicePoolConfig() {
    return this.appConfig.getPmsPlanCreatorServicePoolConfig();
  }

  @Provides
  @Singleton
  @Named("opaClientConfig")
  public ServiceHttpClientConfig opaClientConfig() {
    return this.appConfig.getOpaClientConfig();
  }

  @Provides
  @Singleton
  @Named("policyManagerSecret")
  public String policyManagerSecret() {
    return this.appConfig.getPolicyManagerSecret();
  }

  @Provides
  @Singleton
  @Named("ciExecutionServiceConfig")
  public CIExecutionServiceConfig ciExecutionServiceConfig() {
    return this.appConfig.getCiExecutionServiceConfig();
  }

  @Provides
  @Singleton
  @Named("segmentConfiguration")
  public SegmentConfiguration segmentConfiguration() {
    return this.appConfig.getSegmentConfiguration();
  }

  @Provides
  @Singleton
  @Named("iacmServiceConfig")
  public IACMServiceConfig iacmServiceConfig() {
    return this.appConfig.getIacmServiceConfig();
  }

  @Provides
  @Singleton
  @Named("enforcementClientConfiguration")
  public EnforcementClientConfiguration enforcementClientConfiguration() {
    return this.appConfig.getEnforcementClientConfiguration();
  }

  @Provides
  @Singleton
  public AsyncWaitEngine asyncWaitEngine(WaitNotifyEngine waitNotifyEngine) {
    return new AsyncWaitEngineImpl(waitNotifyEngine, NG_ORCHESTRATION);
  }

  @Provides
  @Singleton
  @Named("backstageHttpClientConfig")
  public ServiceHttpClientConfig backstageHttpClientConfig() {
    return this.appConfig.getBackstageHttpClientConfig();
  }

  @Provides
  @Singleton
  @Named("proxyAllowList")
  public ProxyAllowListConfig proxyAllowList() {
    return this.appConfig.getProxyAllowList();
  }

  @Provides
  @Singleton
  @Named("notificationConfigs")
  public HashMap<String, String> notificationConfigs() {
    return this.appConfig.getNotificationConfigs();
  }

  @Provides
  @Singleton
  @Named("pipelineServiceClientConfigs")
  public ServiceHttpClientConfig pipelineServiceConfiguration() {
    return this.appConfig.getPipelineServiceConfiguration();
  }

  @Provides
  @Singleton
  @Named("tiServiceConfig")
  public TIServiceConfig tiServiceConfig() {
    return this.appConfig.getTiServiceConfig();
  }

  @Provides
  @Singleton
  @Named("scorecardScoreComputationIteratorConfig")
  public IteratorConfig scorecardScoreComputationIteratorConfig() {
    return this.appConfig.getScorecardScoreComputationIteratorConfig();
  }

  @Provides
  @Singleton
  @Named("idpServiceSecret")
  public String idpServiceSecret() {
    return this.appConfig.getIdpServiceSecret();
  }

  @Provides
  @Singleton
  @Named("auditClientConfig")
  public ServiceHttpClientConfig auditClientConfig() {
    return this.appConfig.getAuditClientConfig();
  }

  @Provides
  @Singleton
  @Named("enableAudit")
  public Boolean enableAudit() {
    return this.appConfig.isEnableAudit();
  }

  @Provides
  @Singleton
  @Named("internalAccounts")
  public List<String> internalAccounts() {
    return this.appConfig.getInternalAccounts();
  }

  @Provides
  @Singleton
  @Named("harnessCodeGitBaseUrl")
  String getHarnessCodeGitBaseUrl() {
    String gitUrl = this.appConfig.getHarnessCodeGitUrl();
    if (gitUrl.endsWith("/")) {
      return gitUrl.substring(0, gitUrl.length() - 1);
    }
    return gitUrl;
  }
}
