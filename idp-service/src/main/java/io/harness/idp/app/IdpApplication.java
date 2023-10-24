/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.app;

import static io.harness.NGConstants.X_API_KEY;
import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.authorization.AuthorizationServiceHeader.DEFAULT;
import static io.harness.idp.app.IdpConfiguration.HARNESS_RESOURCE_CLASSES;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;

import static java.util.Collections.singletonList;

import io.harness.Microservice;
import io.harness.ModuleType;
import io.harness.PipelineServiceUtilityModule;
import io.harness.SCMGrpcClientModule;
import io.harness.accesscontrol.NGAccessDeniedExceptionMapper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.cache.CacheModule;
import io.harness.ci.execution.execution.OrchestrationExecutionEventHandlerRegistrar;
import io.harness.ci.execution.plan.creator.CIModuleInfoProvider;
import io.harness.ci.execution.plan.creator.filter.CIFilterCreationResponseMerger;
import io.harness.ci.registrars.ExecutionAdvisers;
import io.harness.ff.FeatureFlagService;
import io.harness.health.HealthMonitor;
import io.harness.health.HealthService;
import io.harness.idp.annotations.IdpServiceAuth;
import io.harness.idp.annotations.IdpServiceAuthIfHasApiKey;
import io.harness.idp.configmanager.jobs.ConfigPurgeJob;
import io.harness.idp.envvariable.jobs.BackstageEnvVariablesSyncJob;
import io.harness.idp.events.consumers.EntityCrudStreamConsumer;
import io.harness.idp.events.consumers.IdpEventConsumerController;
import io.harness.idp.events.consumers.IdpModuleLicenseUsageCaptureEventConsumer;
import io.harness.idp.license.usage.jobs.LicenseUsageDailyCountJob;
import io.harness.idp.license.usage.resources.IDPLicenseUsageResource;
import io.harness.idp.migration.IdpMigrationProvider;
import io.harness.idp.namespace.jobs.DefaultAccountIdToNamespaceMappingForPrEnv;
import io.harness.idp.pipeline.provider.IdpPipelineServiceInfoProvider;
import io.harness.idp.pipeline.registrar.IdpStepRegistrar;
import io.harness.idp.scorecard.scores.iteratorhandler.ScoreComputationHandler;
import io.harness.idp.user.jobs.UserSyncJob;
import io.harness.licensing.usage.resources.LicenseUsageResource;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.MetricRegistryModule;
import io.harness.metrics.service.api.MetricService;
import io.harness.migration.MigrationProvider;
import io.harness.migration.NGMigrationSdkInitHelper;
import io.harness.migration.NGMigrationSdkModule;
import io.harness.migration.beans.NGMigrationConfiguration;
import io.harness.mongo.iterator.IteratorConfig;
import io.harness.ng.core.exceptionmappers.GenericExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.JerseyViolationExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.NotAllowedExceptionMapper;
import io.harness.ng.core.exceptionmappers.NotFoundExceptionMapper;
import io.harness.ng.core.exceptionmappers.WingsExceptionMapperV2;
import io.harness.notification.Team;
import io.harness.notification.module.NotificationClientModule;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.templates.PredefinedTemplate;
import io.harness.outbox.OutboxEventPollService;
import io.harness.persistence.HPersistence;
import io.harness.pms.events.base.PipelineEventConsumerController;
import io.harness.pms.listener.NgOrchestrationNotifyEventListenerNonVersioned;
import io.harness.pms.sdk.PmsSdkConfiguration;
import io.harness.pms.sdk.PmsSdkInitHelper;
import io.harness.pms.sdk.PmsSdkModule;
import io.harness.pms.sdk.core.SdkDeployMode;
import io.harness.pms.sdk.execution.events.facilitators.FacilitatorEventRedisConsumer;
import io.harness.pms.sdk.execution.events.facilitators.FacilitatorEventRedisConsumerV2;
import io.harness.pms.sdk.execution.events.interrupts.InterruptEventRedisConsumer;
import io.harness.pms.sdk.execution.events.interrupts.InterruptEventRedisConsumerV2;
import io.harness.pms.sdk.execution.events.node.advise.NodeAdviseEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.advise.NodeAdviseRedisConsumerV2;
import io.harness.pms.sdk.execution.events.node.resume.NodeResumeEventConsumerV2;
import io.harness.pms.sdk.execution.events.node.resume.NodeResumeEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.start.NodeStartEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.start.NodeStartEventRedisConsumerV2;
import io.harness.pms.sdk.execution.events.orchestrationevent.OrchestrationEventRedisConsumer;
import io.harness.pms.sdk.execution.events.plan.CreatePartialPlanRedisConsumer;
import io.harness.pms.sdk.execution.events.progress.NodeProgressEventRedisConsumerV2;
import io.harness.pms.sdk.execution.events.progress.ProgressEventRedisConsumer;
import io.harness.pms.serializer.json.PmsBeansJacksonModule;
import io.harness.queue.QueueListenerController;
import io.harness.queue.QueuePublisher;
import io.harness.request.RequestContextFilter;
import io.harness.request.RequestLoggingFilter;
import io.harness.security.InternalApiAuthFilter;
import io.harness.security.NextGenAuthenticationFilter;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.service.impl.DelegateAsyncServiceImpl;
import io.harness.service.impl.DelegateProgressServiceImpl;
import io.harness.service.impl.DelegateSyncServiceImpl;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;
import io.harness.token.remote.TokenClient;
import io.harness.waiter.NotifyEvent;
import io.harness.waiter.NotifyQueuePublisherRegister;
import io.harness.yaml.YamlSdkConfiguration;
import io.harness.yaml.YamlSdkInitHelper;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dirkraft.dropwizard.fileassets.FileAssetsBundle;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.serializer.HObjectMapper;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.LogManager;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.glassfish.jersey.server.ServerProperties;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * The main application - entry point for the entire Wings Application.
 */
@Slf4j
@OwnedBy(IDP)
public class IdpApplication extends Application<IdpConfiguration> {
  private final MetricRegistry metricRegistry = new MetricRegistry();

  /**
   * The entry point of application.
   *
   * @param args the input arguments
   * @throws Exception the exception
   */
  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));

    new IdpApplication().run(args);
  }

  public static void configureObjectMapper(final ObjectMapper mapper) {
    HObjectMapper.configureObjectMapperForNG(mapper);
    mapper.registerModule(new PmsBeansJacksonModule());
  }

  @Override
  public String getName() {
    return "IDP Service";
  }

  @Override
  public void initialize(Bootstrap<IdpConfiguration> bootstrap) {
    initializeLogging();
    log.info("bootstrapping ...");
    bootstrap.addCommand(new InspectCommand<>(this));
    bootstrap.addCommand(new ScanClasspathMetadataCommand());

    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    bootstrap.addBundle(new FileAssetsBundle("/.well-known"));
    bootstrap.setMetricRegistry(metricRegistry);

    log.info("bootstrapping done.");
  }

  @Override
  public void run(final IdpConfiguration configuration, Environment environment) throws Exception {
    log.info("Starting app ...");
    log.info("Entering startup maintenance mode");
    MaintenanceController.forceMaintenance(true);

    ExecutorModule.getInstance().setExecutorService(ThreadPool.create(
        20, 1000, 500L, TimeUnit.MILLISECONDS, new ThreadFactoryBuilder().setNameFormat("main-app-pool-%d").build()));

    List<Module> modules = new ArrayList<>();
    modules.add(new IdpModule(configuration));
    modules.add(new CacheModule(configuration.getCacheConfig()));
    PmsSdkConfiguration idpPmsSdkConfiguration = getPmsSdkConfiguration(configuration);
    modules.add(PmsSdkModule.getInstance(idpPmsSdkConfiguration));
    modules.add(PipelineServiceUtilityModule.getInstance());
    modules.add(NGMigrationSdkModule.getInstance());
    modules.add(new SCMGrpcClientModule(configuration.getScmConnectionConfig()));
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(FeatureFlagService.class).toProvider(Providers.of(null));
      }
    });
    modules.add(new NotificationClientModule(configuration.getNotificationClientConfiguration()));
    modules.add(new MetricRegistryModule(metricRegistry));

    Injector injector = Guice.createInjector(modules);
    registerPMSSDK(configuration, injector);
    registerResources(environment, injector);
    registerHealthChecksManager(environment, injector);
    registerQueueListeners(injector);
    registerAuthFilters(configuration, environment, injector);
    registerManagedJobs(environment, injector);
    registerPmsSdkEvents(injector);
    registerExceptionMappers(environment.jersey());
    registerMigrations(injector);
    registerHealthCheck(environment, injector);
    registerYamlSdk(injector);
    registerWaitEnginePublishers(injector);
    registerNotificationTemplates(configuration, injector);
    registerRequestContextFilter(environment);
    registerIterators(injector, configuration.getScorecardScoreComputationIteratorConfig());
    environment.jersey().register(RequestLoggingFilter.class);
    environment.jersey().register(injector.getInstance(IdpServiceRequestInterceptor.class));

    //    initMetrics(injector);
    log.info("Starting app done");
    log.info("IDP Service is running on JRE: {}", System.getProperty("java.version"));

    MaintenanceController.forceMaintenance(false);
  }

  private void registerManagedJobs(Environment environment, Injector injector) {
    environment.lifecycle().manage(injector.getInstance(BackstageEnvVariablesSyncJob.class));
    environment.lifecycle().manage(injector.getInstance(UserSyncJob.class));
    environment.lifecycle().manage(injector.getInstance(ConfigPurgeJob.class));
    environment.lifecycle().manage(injector.getInstance(DefaultAccountIdToNamespaceMappingForPrEnv.class));
    environment.lifecycle().manage(injector.getInstance(PipelineEventConsumerController.class));
    environment.lifecycle().manage(injector.getInstance(OutboxEventPollService.class));
    environment.lifecycle().manage(injector.getInstance(LicenseUsageDailyCountJob.class));
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateSyncServiceImpl.class), 0L, 2L, TimeUnit.SECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateAsyncServiceImpl.class), 0L, 5L, TimeUnit.SECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateProgressServiceImpl.class), 0L, 5L, TimeUnit.SECONDS);
  }

  private void registerQueueListeners(Injector injector) {
    log.info("Initializing queue listeners...");
    IdpEventConsumerController controller = injector.getInstance(IdpEventConsumerController.class);
    controller.register(injector.getInstance(EntityCrudStreamConsumer.class), 1);
    QueueListenerController queueListenerController = injector.getInstance(QueueListenerController.class);
    queueListenerController.register(injector.getInstance(NgOrchestrationNotifyEventListenerNonVersioned.class), 1);
    controller.register(injector.getInstance(IdpModuleLicenseUsageCaptureEventConsumer.class), 2);
  }

  private void registerIterators(Injector injector, IteratorConfig iteratorConfig) {
    if (iteratorConfig.isEnabled()) {
      injector.getInstance(ScoreComputationHandler.class).registerIterators(iteratorConfig);
    }
  }

  private void registerRequestContextFilter(Environment environment) {
    environment.jersey().register(new RequestContextFilter());
  }

  private void initMetrics(Injector injector) {
    injector.getInstance(MetricService.class).initializeMetrics();
  }

  private void registerHealthChecksManager(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("IDP Service", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : HARNESS_RESOURCE_CLASSES) {
      environment.jersey().register(injector.getInstance(resource));
    }
    environment.jersey().register(injector.getInstance(LicenseUsageResource.class));
    environment.jersey().register(injector.getInstance(IDPLicenseUsageResource.class));
    environment.jersey().property(ServerProperties.RESOURCE_VALIDATION_DISABLE, true);
  }

  private void registerAuthFilters(IdpConfiguration config, Environment environment, Injector injector) {
    Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate = resourceInfoAndRequest
        -> (resourceInfoAndRequest.getKey().getResourceMethod() != null
               && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(IdpServiceAuth.class) != null)
        || (resourceInfoAndRequest.getKey().getResourceClass() != null
            && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(IdpServiceAuth.class) != null)
        || (resourceInfoAndRequest.getKey().getResourceMethod() != null
            && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(IdpServiceAuthIfHasApiKey.class)
                != null
            && resourceInfoAndRequest.getValue().getHeaders().get(X_API_KEY) != null)
        || (resourceInfoAndRequest.getKey().getResourceMethod() != null
            && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(NextGenManagerAuth.class) != null)
        || (resourceInfoAndRequest.getKey().getResourceClass() != null
            && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(NextGenManagerAuth.class) != null);
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(AuthorizationServiceHeader.BEARER.getServiceId(), config.getJwtAuthSecret());
    serviceToSecretMapping.put(
        AuthorizationServiceHeader.IDENTITY_SERVICE.getServiceId(), config.getJwtIdentityServiceSecret());
    serviceToSecretMapping.put(AuthorizationServiceHeader.DEFAULT.getServiceId(), config.getNgManagerServiceSecret());
    serviceToSecretMapping.put(AuthorizationServiceHeader.IDP_UI.getServiceId(), config.getIdpServiceSecret());
    serviceToSecretMapping.put(AuthorizationServiceHeader.IDP_SERVICE.getServiceId(), config.getIdpServiceSecret());
    serviceToSecretMapping.put(
        AuthorizationServiceHeader.ADMIN_PORTAL.getServiceId(), config.getJwtExternalServiceSecret());
    environment.jersey().register(new NextGenAuthenticationFilter(predicate, null, serviceToSecretMapping,
        injector.getInstance(Key.get(TokenClient.class, Names.named("PRIVILEGED")))));
    registerInternalApiAuthFilter(config, environment);
  }

  private void registerInternalApiAuthFilter(IdpConfiguration configuration, Environment environment) {
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(DEFAULT.getServiceId(), configuration.getIdpServiceSecret());
    environment.jersey().register(
        new InternalApiAuthFilter(getAuthFilterPredicate(InternalApi.class), null, serviceToSecretMapping));
  }

  private Predicate<Pair<ResourceInfo, ContainerRequestContext>> getAuthFilterPredicate(
      Class<? extends Annotation> annotation) {
    return resourceInfoAndRequest
        -> (resourceInfoAndRequest.getKey().getResourceMethod() != null
               && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(annotation) != null)
        || (resourceInfoAndRequest.getKey().getResourceClass() != null
            && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(annotation) != null);
  }

  private void registerExceptionMappers(JerseyEnvironment jersey) {
    jersey.register(JerseyViolationExceptionMapperV2.class);
    jersey.register(GenericExceptionMapperV2.class);
    jersey.register(new JsonProcessingExceptionMapper(true));
    jersey.register(EarlyEofExceptionMapper.class);
    jersey.register(NGAccessDeniedExceptionMapper.class);
    jersey.register(WingsExceptionMapperV2.class);
    jersey.register(NotFoundExceptionMapper.class);
    jersey.register(NotAllowedExceptionMapper.class);
  }

  private void registerMigrations(Injector injector) {
    NGMigrationConfiguration config = getMigrationSdkConfiguration();
    NGMigrationSdkInitHelper.initialize(injector, config);
  }

  private NGMigrationConfiguration getMigrationSdkConfiguration() {
    return NGMigrationConfiguration.builder()
        .microservice(Microservice.IDP)
        .migrationProviderList(new ArrayList<Class<? extends MigrationProvider>>() {
          { add(IdpMigrationProvider.class); }
        })
        .build();
  }

  private PmsSdkConfiguration getPmsSdkConfiguration(IdpConfiguration configuration) {
    boolean remote = configuration.getShouldConfigureWithPMS() != null && configuration.getShouldConfigureWithPMS();

    return PmsSdkConfiguration.builder()
        .streamPerServiceConfiguration(configuration.isStreamPerServiceConfiguration())
        .deploymentMode(remote ? SdkDeployMode.REMOTE : SdkDeployMode.LOCAL)
        .moduleType(ModuleType.IDP)
        .grpcServerConfig(configuration.getPmsSdkGrpcServerConfig())
        .pmsGrpcClientConfig(configuration.getPmsGrpcClientConfig())
        .pipelineServiceInfoProviderClass(IdpPipelineServiceInfoProvider.class)
        .filterCreationResponseMerger(new CIFilterCreationResponseMerger())
        .engineSteps(IdpStepRegistrar.getEngineSteps())
        .engineAdvisers(ExecutionAdvisers.getEngineAdvisers())
        .engineEventHandlersMap(OrchestrationExecutionEventHandlerRegistrar.getEngineEventHandlers())
        .eventsFrameworkConfiguration(configuration.getEventsFrameworkConfiguration())
        .executionPoolConfig(configuration.getPmsSdkExecutionPoolConfig())
        .orchestrationEventPoolConfig(configuration.getPmsSdkOrchestrationEventPoolConfig())
        .executionSummaryModuleInfoProviderClass(CIModuleInfoProvider.class)
        .eventsFrameworkConfiguration(configuration.getEventsFrameworkConfiguration())
        .build();
  }

  private void registerPMSSDK(IdpConfiguration configuration, Injector injector) {
    PmsSdkConfiguration idpSDKConfig = getPmsSdkConfiguration(configuration);
    if (idpSDKConfig.getDeploymentMode().equals(SdkDeployMode.REMOTE)) {
      try {
        PmsSdkInitHelper.initializeSDKInstance(injector, idpSDKConfig);
      } catch (Exception e) {
        log.error("PMS SDK registration failed", e);
      }
    }
  }

  private void registerPmsSdkEvents(Injector injector) {
    log.info("Initializing pms sdk redis abstract consumers...");
    PipelineEventConsumerController pipelineEventConsumerController =
        injector.getInstance(PipelineEventConsumerController.class);
    pipelineEventConsumerController.register(injector.getInstance(OrchestrationEventRedisConsumer.class), 1);

    pipelineEventConsumerController.register(injector.getInstance(InterruptEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(FacilitatorEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeStartEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(ProgressEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeAdviseEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeResumeEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(CreatePartialPlanRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeAdviseEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeResumeEventRedisConsumer.class), 1);

    pipelineEventConsumerController.register(injector.getInstance(InterruptEventRedisConsumerV2.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(FacilitatorEventRedisConsumerV2.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeStartEventRedisConsumerV2.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeProgressEventRedisConsumerV2.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeAdviseRedisConsumerV2.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeResumeEventConsumerV2.class), 1);

    pipelineEventConsumerController.register(injector.getInstance(CreatePartialPlanRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeAdviseEventRedisConsumer.class), 2);
    pipelineEventConsumerController.register(injector.getInstance(NodeResumeEventRedisConsumer.class), 2);
    pipelineEventConsumerController.register(injector.getInstance(CreatePartialPlanRedisConsumer.class), 2);
  }

  private void registerWaitEnginePublishers(Injector injector) {
    final QueuePublisher<NotifyEvent> publisher =
        injector.getInstance(Key.get(new TypeLiteral<QueuePublisher<NotifyEvent>>() {}));
    final NotifyQueuePublisherRegister notifyQueuePublisherRegister =
        injector.getInstance(NotifyQueuePublisherRegister.class);
    notifyQueuePublisherRegister.register(
        NG_ORCHESTRATION, payload -> publisher.send(singletonList(NG_ORCHESTRATION), payload));
  }

  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("IDP", healthService);
    healthService.registerMonitor((HealthMonitor) injector.getInstance(MongoTemplate.class));
  }

  private void registerYamlSdk(Injector injector) {
    YamlSdkConfiguration yamlSdkConfiguration = YamlSdkConfiguration.builder()
                                                    .requireSchemaInit(true)
                                                    .requireSnippetInit(true)
                                                    .requireValidatorInit(false)
                                                    .build();
    YamlSdkInitHelper.initialize(injector, yamlSdkConfiguration);
  }

  private void registerNotificationTemplates(IdpConfiguration configuration, Injector injector) {
    NotificationClient notificationClient = injector.getInstance(NotificationClient.class);
    List<PredefinedTemplate> templates =
        new ArrayList<>(List.of(PredefinedTemplate.IDP_PLUGIN_REQUESTS_NOTIFICATION_SLACK));

    if (configuration.getShouldConfigureWithNotification()) {
      for (PredefinedTemplate template : templates) {
        try {
          log.info("Registering {} with NotificationService", template);
          notificationClient.saveNotificationTemplate(Team.IDP, template, true);
        } catch (Exception ex) {
          log.error(
              "Unable to save {} to NotificationService - skipping register notification templates.", template, ex);
        }
      }
    }
  }
}
