/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.NGConstants.X_API_KEY;
import static io.harness.PipelineServiceConfiguration.HARNESS_RESOURCE_CLASSES;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.authorization.AuthorizationServiceHeader.DEFAULT;
import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.configuration.DeployVariant.DEPLOY_VERSION;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.pms.async.plan.PlanNotifyEventConsumer.PMS_PLAN_CREATION;
import static io.harness.pms.contracts.plan.ExpansionRequestType.KEY;
import static io.harness.waiter.PmsNotifyEventListener.PMS_ORCHESTRATION;

import static com.google.common.collect.ImmutableMap.of;

import io.harness.accesscontrol.NGAccessDeniedExceptionMapper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.cache.CacheModule;
import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.configuration.DeployVariant;
import io.harness.consumers.GraphUpdateRedisConsumer;
import io.harness.controller.PrimaryVersionChangeScheduler;
import io.harness.delay.DelayEventListener;
import io.harness.enforcement.MaxStaticValueRestrictionUsageImpl;
import io.harness.enforcement.client.CustomRestrictionRegisterConfiguration;
import io.harness.enforcement.client.RestrictionUsageRegisterConfiguration;
import io.harness.enforcement.client.custom.CustomRestrictionInterface;
import io.harness.enforcement.client.services.EnforcementSdkRegisterService;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.engine.events.NodeExecutionStatusUpdateEventHandler;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.node.NodeExecutionServiceImpl;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.plan.PlanExecutionServiceImpl;
import io.harness.engine.expressions.OrchestrationConstants;
import io.harness.engine.interrupts.InterruptMonitor;
import io.harness.engine.interrupts.OrchestrationEndInterruptHandler;
import io.harness.engine.pms.execution.strategy.plan.PlanExecutionStrategy;
import io.harness.engine.pms.start.NodeStartHelper;
import io.harness.engine.timeouts.TimeoutInstanceRemover;
import io.harness.event.OrchestrationEndGraphHandler;
import io.harness.event.OrchestrationLogPublisher;
import io.harness.event.OrchestrationStartEventHandler;
import io.harness.event.PipelineExecutionSummaryDeleteObserver;
import io.harness.event.PipelineResourceRestraintInstanceDeleteObserver;
import io.harness.event.PlanExecutionMetadataDeleteObserver;
import io.harness.exception.GeneralException;
import io.harness.execution.consumers.InitiateNodeEventRedisConsumer;
import io.harness.execution.consumers.SdkResponseEventRedisConsumer;
import io.harness.ff.FeatureFlagConfig;
import io.harness.gitsync.AbstractGitSyncSdkModule;
import io.harness.gitsync.GitSdkConfiguration;
import io.harness.gitsync.GitSyncEntitiesConfiguration;
import io.harness.gitsync.GitSyncSdkConfiguration;
import io.harness.gitsync.GitSyncSdkInitHelper;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.persistance.NoOpGitSyncSdkServiceImpl;
import io.harness.gitsync.persistance.testing.NoOpGitAwarePersistenceImpl;
import io.harness.govern.ProviderModule;
import io.harness.governance.DefaultConnectorRefExpansionHandler;
import io.harness.graph.stepDetail.PmsGraphStepDetailsServiceImpl;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.health.HealthMonitor;
import io.harness.health.HealthService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.metrics.MetricRegistryModule;
import io.harness.metrics.PipelineTelemetryRecordsJob;
import io.harness.migration.MigrationProvider;
import io.harness.migration.NGMigrationSdkInitHelper;
import io.harness.migration.NGMigrationSdkModule;
import io.harness.migration.beans.NGMigrationConfiguration;
import io.harness.ng.DbAliases;
import io.harness.ng.core.CorrelationFilter;
import io.harness.ng.core.TraceFilter;
import io.harness.ng.core.exceptionmappers.GenericExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.JerseyViolationExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.NotAllowedExceptionMapper;
import io.harness.ng.core.exceptionmappers.NotFoundExceptionMapper;
import io.harness.ng.core.exceptionmappers.WingsExceptionMapperV2;
import io.harness.ng.core.filter.ApiResponseFilter;
import io.harness.notification.module.NotificationClientModule;
import io.harness.observers.PipelineExecutionSummaryFailureInfoUpdateHandler;
import io.harness.outbox.OutboxEventPollService;
import io.harness.persistence.HPersistence;
import io.harness.persistence.store.Store;
import io.harness.plancreator.pipeline.PipelineConfig;
import io.harness.plancreator.strategy.StrategyConstants;
import io.harness.plancreator.strategy.StrategyMaxConcurrencyRestrictionUsageImpl;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.annotations.PipelineServiceAuthIfHasApiKey;
import io.harness.pms.approval.ApprovalInstanceExpirationJob;
import io.harness.pms.approval.ApprovalInstanceHandler;
import io.harness.pms.async.plan.PlanNotifyEventPublisher;
import io.harness.pms.contracts.plan.JsonExpansionInfo;
import io.harness.pms.event.PMSEventConsumerService;
import io.harness.pms.event.overviewLandingPage.PipelineExecutionSummaryRedisEventConsumer;
import io.harness.pms.event.overviewLandingPage.PipelineExecutionSummaryRedisEventConsumerSnapshot;
import io.harness.pms.event.pollingevent.PollingEventStreamConsumer;
import io.harness.pms.event.webhookevent.WebhookEventStreamConsumer;
import io.harness.pms.events.base.PipelineEventConsumerController;
import io.harness.pms.inputset.gitsync.InputSetEntityGitSyncHelper;
import io.harness.pms.inputset.gitsync.InputSetYamlDTO;
import io.harness.pms.instrumentaion.InstrumentationPipelineEndEventHandler;
import io.harness.pms.migration.PipelineCoreMigrationProvider;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.observers.InputSetPipelineObserver;
import io.harness.pms.notification.orchestration.handlers.NodeExecutionOutboxHandler;
import io.harness.pms.notification.orchestration.handlers.NotificationInformHandler;
import io.harness.pms.notification.orchestration.handlers.StageStartNotificationHandler;
import io.harness.pms.notification.orchestration.handlers.StageStatusUpdateNotificationEventHandler;
import io.harness.pms.outbox.PipelineOutboxEventHandler;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineSetupUsageHelper;
import io.harness.pms.pipeline.gitsync.PipelineEntityGitSyncHelper;
import io.harness.pms.pipeline.observer.PipelineEntityCrudObserver;
import io.harness.pms.pipeline.observer.PipelineMetadataObserver;
import io.harness.pms.plan.creation.PipelineServiceFilterCreationResponseMerger;
import io.harness.pms.plan.creation.PipelineServiceInternalInfoProvider;
import io.harness.pms.plan.execution.PmsExecutionServiceInfoProvider;
import io.harness.pms.plan.execution.handlers.ExecutionInfoUpdateEventHandler;
import io.harness.pms.plan.execution.handlers.ExecutionSummaryCreateEventHandler;
import io.harness.pms.plan.execution.handlers.PipelineStatusUpdateEventHandler;
import io.harness.pms.plan.execution.handlers.PlanStatusEventEmitterHandler;
import io.harness.pms.sdk.PmsSdkConfiguration;
import io.harness.pms.sdk.PmsSdkInitHelper;
import io.harness.pms.sdk.PmsSdkInstanceCacheMonitor;
import io.harness.pms.sdk.PmsSdkModule;
import io.harness.pms.sdk.core.SdkDeployMode;
import io.harness.pms.sdk.core.governance.JsonExpansionHandlerInfo;
import io.harness.pms.sdk.execution.events.facilitators.FacilitatorEventRedisConsumer;
import io.harness.pms.sdk.execution.events.interrupts.InterruptEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.advise.NodeAdviseEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.resume.NodeResumeEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.start.NodeStartEventRedisConsumer;
import io.harness.pms.sdk.execution.events.orchestrationevent.OrchestrationEventRedisConsumer;
import io.harness.pms.sdk.execution.events.progress.ProgressEventRedisConsumer;
import io.harness.pms.serializer.json.PmsBeansJacksonModule;
import io.harness.pms.tags.OrchestrationEndTagsResolveHandler;
import io.harness.pms.triggers.scheduled.ScheduledTriggerHandler;
import io.harness.pms.triggers.webhook.service.TriggerWebhookExecutionService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.queue.QueueListenerController;
import io.harness.registrars.PipelineServiceFacilitatorRegistrar;
import io.harness.registrars.PipelineServiceStepRegistrar;
import io.harness.request.RequestContextFilter;
import io.harness.resource.VersionInfoResource;
import io.harness.security.InternalApiAuthFilter;
import io.harness.security.NextGenAuthenticationFilter;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.serializer.PipelineServiceUtilAdviserRegistrar;
import io.harness.serializer.jackson.PipelineServiceJacksonModule;
import io.harness.service.impl.DelegateAsyncServiceImpl;
import io.harness.service.impl.DelegateProgressServiceImpl;
import io.harness.service.impl.DelegateSyncServiceImpl;
import io.harness.springdata.HMongoTemplate;
import io.harness.steps.approval.step.custom.CustomApprovalInstanceHandler;
import io.harness.steps.barriers.BarrierInitializer;
import io.harness.steps.barriers.event.BarrierDropper;
import io.harness.steps.barriers.event.BarrierPositionHelperEventHandler;
import io.harness.steps.barriers.service.BarrierServiceImpl;
import io.harness.steps.common.NodeExecutionMetadataDeleteObserver;
import io.harness.steps.resourcerestraint.ResourceRestraintInitializer;
import io.harness.steps.resourcerestraint.ResourceRestraintObserver;
import io.harness.steps.resourcerestraint.service.ResourceRestraintPersistenceMonitor;
import io.harness.telemetry.TelemetryReporter;
import io.harness.telemetry.filter.APIAuthTelemetryFilter;
import io.harness.telemetry.filter.APIAuthTelemetryResponseFilter;
import io.harness.telemetry.filter.APIErrorsTelemetrySenderFilter;
import io.harness.telemetry.filter.TerraformTelemetryFilter;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;
import io.harness.timeout.TimeoutEngine;
import io.harness.token.remote.TokenClient;
import io.harness.tracing.MongoRedisTracer;
import io.harness.waiter.NotifierScheduledExecutorService;
import io.harness.waiter.NotifyQueuePublisherRegister;
import io.harness.waiter.NotifyResponseCleaner;
import io.harness.waiter.NotifyResponseCleanerSpringPersistence;
import io.harness.waiter.PmsNotifyEventConsumerRedis;
import io.harness.waiter.PmsNotifyEventListener;
import io.harness.waiter.PmsNotifyEventPublisher;
import io.harness.waiter.ProgressUpdateService;
import io.harness.yaml.YamlSdkConfiguration;
import io.harness.yaml.YamlSdkInitHelper;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import io.serializer.HObjectMapper;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import java.lang.annotation.Annotation;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@OwnedBy(PIPELINE)
public class PipelineServiceApplication extends Application<PipelineServiceConfiguration> {
  private static final SecureRandom random = new SecureRandom();
  private static final String APPLICATION_NAME = "Pipeline Service Application";

  private final MetricRegistry metricRegistry = new MetricRegistry();
  private HarnessMetricRegistry harnessMetricRegistry;

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));

    new PipelineServiceApplication().run(args);
  }

  @Override
  public String getName() {
    return APPLICATION_NAME;
  }

  @Override
  public void initialize(Bootstrap<PipelineServiceConfiguration> bootstrap) {
    initializeLogging();
    bootstrap.addCommand(new InspectCommand<>(this));
    bootstrap.addCommand(new ScanClasspathMetadataCommand());
    bootstrap.addCommand(new GenerateOpenApiSpecCommand());

    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    configureObjectMapper(bootstrap.getObjectMapper());
    bootstrap.addBundle(new SwaggerBundle<PipelineServiceConfiguration>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(PipelineServiceConfiguration appConfig) {
        return appConfig.getSwaggerBundleConfiguration();
      }
    });
  }

  public static void configureObjectMapper(final ObjectMapper mapper) {
    HObjectMapper.configureObjectMapperForNG(mapper);
    mapper.registerModule(new PmsBeansJacksonModule());
    mapper.registerModule(new PipelineServiceJacksonModule());
  }

  @Override
  public void run(PipelineServiceConfiguration appConfig, Environment environment) {
    log.info("Starting Pipeline Service Application ...");
    MaintenanceController.forceMaintenance(true);

    ExecutorModule.getInstance().setExecutorService(ThreadPool.create(appConfig.getCommonPoolConfig().getCorePoolSize(),
        appConfig.getCommonPoolConfig().getMaxPoolSize(), appConfig.getCommonPoolConfig().getIdleTime(),
        appConfig.getCommonPoolConfig().getTimeUnit(),
        new ThreadFactoryBuilder().setNameFormat("main-app-pool-%d").build()));
    List<Module> modules = new ArrayList<>();
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      PipelineServiceConfiguration configuration() {
        return appConfig;
      }

      @Provides
      @Singleton
      @Named("dbAliases")
      public List<String> getDbAliases() {
        return appConfig.getDbAliases();
      }
    });
    modules.add(new AbstractCfModule() {
      @Override
      public CfClientConfig cfClientConfig() {
        return appConfig.getCfClientConfig();
      }

      @Override
      public CfMigrationConfig cfMigrationConfig() {
        return CfMigrationConfig.builder().build();
      }

      @Override
      public FeatureFlagConfig featureFlagConfig() {
        return appConfig.getFeatureFlagConfig();
      }
    });
    modules.add(new NotificationClientModule(appConfig.getNotificationClientConfiguration()));
    modules.add(PipelineServiceModule.getInstance(appConfig));
    modules.add(new MetricRegistryModule(metricRegistry));
    modules.add(NGMigrationSdkModule.getInstance());
    CacheModule cacheModule = new CacheModule(appConfig.getCacheConfig());
    modules.add(cacheModule);
    if (appConfig.isShouldDeployWithGitSync()) {
      GitSyncSdkConfiguration gitSyncSdkConfiguration = getGitSyncConfiguration(appConfig);
      modules.add(new AbstractGitSyncSdkModule() {
        @Override
        public GitSyncSdkConfiguration getGitSyncSdkConfiguration() {
          return gitSyncSdkConfiguration;
        }
      });
    } else {
      modules.add(new SCMGrpcClientModule(appConfig.getGitSdkConfiguration().getScmConnectionConfig()));
      modules.add(new AbstractGitSyncSdkModule() {
        @Override
        protected void configure() {
          bind(GitAwarePersistence.class).to(NoOpGitAwarePersistenceImpl.class);
          bind(GitSyncSdkService.class).to(NoOpGitSyncSdkServiceImpl.class);
        }

        @Override
        public GitSyncSdkConfiguration getGitSyncSdkConfiguration() {
          return null;
        }
      });
    }

    // Pipeline Service Modules
    PmsSdkConfiguration pmsSdkConfiguration = getPmsSdkConfiguration(appConfig);
    modules.add(PmsSdkModule.getInstance(pmsSdkConfiguration));
    modules.add(PipelineServiceUtilityModule.getInstance());
    Injector injector = Guice.createInjector(modules);
    registerStores(appConfig, injector);
    registerEventListeners(injector);
    registerWaitEnginePublishers(injector);
    registerScheduledJobs(injector, appConfig);
    registerCorsFilter(appConfig, environment);
    registerResources(environment, injector);
    registerJerseyProviders(environment, injector);
    registerManagedBeans(environment, injector);
    registerAuthFilters(appConfig, environment, injector);
    registerAPIAuthTelemetryFilters(appConfig, environment, injector);
    registerApiResponseFilter(environment, injector);
    registerHealthCheck(environment, injector);
    registerObservers(appConfig, injector);
    registerRequestContextFilter(environment);
    registerOasResource(appConfig, environment, injector);
    intializeSdkInstanceCacheSync(injector);
    initializeEnforcementSdk(injector);

    harnessMetricRegistry = injector.getInstance(HarnessMetricRegistry.class);
    PipelineServiceIteratorsConfig iteratorsConfig = appConfig.getIteratorsConfig();
    injector.getInstance(TriggerWebhookExecutionService.class)
        .registerIterators(iteratorsConfig.getTriggerWebhookConfig());
    injector.getInstance(ScheduledTriggerHandler.class).registerIterators(iteratorsConfig.getScheduleTriggerConfig());
    injector.getInstance(TimeoutEngine.class)
        .createAndStartIterator(PersistenceIteratorFactory.PumpExecutorOptions.builder()
                                    .name("TimeoutEngine")
                                    .poolSize(iteratorsConfig.getTimeoutEngineConfig().getThreadPoolCount())
                                    .build(),
            Duration.ofSeconds(iteratorsConfig.getTimeoutEngineConfig().getTargetIntervalInSeconds()));
    injector.getInstance(BarrierServiceImpl.class).registerIterators(iteratorsConfig.getBarrierConfig());
    injector.getInstance(ApprovalInstanceHandler.class).registerIterators();
    injector.getInstance(CustomApprovalInstanceHandler.class)
        .registerIterators(iteratorsConfig.getApprovalInstanceConfig());
    injector.getInstance(ResourceRestraintPersistenceMonitor.class)
        .registerIterators(iteratorsConfig.getResourceRestraintConfig());
    injector.getInstance(InterruptMonitor.class).registerIterators(iteratorsConfig.getInterruptMonitorConfig());
    injector.getInstance(PrimaryVersionChangeScheduler.class).registerExecutors();

    registerYamlSdk(injector);
    if (appConfig.isShouldDeployWithGitSync()) {
      registerGitSyncSdk(appConfig, injector, environment);
    }

    registerCorrelationFilter(environment, injector);

    if (!appConfig.isDisableFreezeNotificationTemplate()) {
      registerNotificationTemplates(injector);
    }
    registerPmsSdkEvents(appConfig.getPipelineServiceConsumersConfig(), injector);

    initializeGrpcServer(injector);
    registerPmsSdk(appConfig, injector);
    registerMigrations(injector);

    if (BooleanUtils.isTrue(appConfig.getEnableOpentelemetry())) {
      registerTraceFilter(environment, injector);
    }

    log.info("PipelineServiceApplication DEPLOY_VERSION = " + System.getenv().get(DEPLOY_VERSION));
    if (DeployVariant.isCommunity(System.getenv().get(DEPLOY_VERSION))) {
      initializePipelineMonitoring(appConfig, injector);
    } else {
      log.info("PipelineServiceApplication DEPLOY_VERSION is not COMMUNITY");
    }

    MaintenanceController.forceMaintenance(false);
  }

  private void intializeSdkInstanceCacheSync(Injector injector) {
    injector.getInstance(PmsSdkInstanceCacheMonitor.class).scheduleCacheSync();
  }

  private void initializePipelineMonitoring(PipelineServiceConfiguration appConfig, Injector injector) {
    log.info("Initializing PipelineMonitoring");
    injector.getInstance(PipelineTelemetryRecordsJob.class).scheduleTasks();
  }

  private void initializeGrpcServer(Injector injector) {
    log.info("Initializing gRPC servers...");
    ServiceManager serviceManager = injector.getInstance(ServiceManager.class).startAsync();
    serviceManager.awaitHealthy();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> serviceManager.stopAsync().awaitStopped()));
  }

  private void registerOasResource(PipelineServiceConfiguration appConfig, Environment environment, Injector injector) {
    OpenApiResource openApiResource = injector.getInstance(OpenApiResource.class);
    openApiResource.setOpenApiConfiguration(appConfig.getOasConfig());
    environment.jersey().register(openApiResource);
  }

  private void registerStores(PipelineServiceConfiguration configuration, Injector injector) {
    final HPersistence persistence = injector.getInstance(HPersistence.class);
    if (isNotEmpty(configuration.getMongoConfig().getUri())) {
      persistence.register(Store.builder().name(DbAliases.PMS).build(), configuration.getMongoConfig().getUri());
    }
  }

  private static void registerObservers(PipelineServiceConfiguration appConfig, Injector injector) {
    PmsGraphStepDetailsServiceImpl pmsGraphStepDetailsService =
        (PmsGraphStepDetailsServiceImpl) injector.getInstance(Key.get(PmsGraphStepDetailsService.class));
    pmsGraphStepDetailsService.getStepDetailsUpdateObserverSubject().register(
        injector.getInstance(Key.get(OrchestrationLogPublisher.class)));

    // Register Pipeline Outbox Observers
    PipelineOutboxEventHandler pipelineOutboxEventHandler =
        injector.getInstance(Key.get(PipelineOutboxEventHandler.class));
    pipelineOutboxEventHandler.getPipelineActionObserverSubject().register(
        injector.getInstance(Key.get(PipelineSetupUsageHelper.class)));
    pipelineOutboxEventHandler.getPipelineActionObserverSubject().register(
        injector.getInstance(Key.get(PipelineEntityCrudObserver.class)));
    pipelineOutboxEventHandler.getPipelineActionObserverSubject().register(
        injector.getInstance(Key.get(InputSetPipelineObserver.class)));
    // PipelineMetadataObserver is also added so that it is also deleted in sync so that runsequence starts with 0 again
    // if same pipeline gets created
    pipelineOutboxEventHandler.getPipelineActionObserverSubject().register(
        injector.getInstance(Key.get(PipelineMetadataObserver.class)));

    NodeExecutionServiceImpl nodeExecutionService =
        (NodeExecutionServiceImpl) injector.getInstance(Key.get(NodeExecutionService.class));

    NodeStartHelper nodeStartHelper = injector.getInstance(Key.get(NodeStartHelper.class));

    // NodeStatusUpdateObserver
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(PlanExecutionService.class)));
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(StageStatusUpdateNotificationEventHandler.class)));
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(BarrierPositionHelperEventHandler.class)));
    nodeExecutionService.getNodeStatusUpdateSubject().register(injector.getInstance(Key.get(BarrierDropper.class)));
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(NodeExecutionStatusUpdateEventHandler.class)));
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(ResourceRestraintObserver.class)));
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(TimeoutInstanceRemover.class)));
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(PipelineExecutionSummaryFailureInfoUpdateHandler.class)));
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(NodeExecutionOutboxHandler.class)));

    // NodeExecutionDeleteObserver
    nodeExecutionService.getNodeDeleteObserverSubject().register(
        injector.getInstance(NodeExecutionMetadataDeleteObserver.class));

    // NodeExecutionStartObserver
    nodeStartHelper.getNodeExecutionStartSubject().register(
        injector.getInstance(Key.get(StageStartNotificationHandler.class)));
    nodeStartHelper.getNodeExecutionStartSubject().register(
        injector.getInstance(Key.get(NodeExecutionOutboxHandler.class)));

    PlanStatusEventEmitterHandler planStatusEventEmitterHandler =
        injector.getInstance(Key.get(PlanStatusEventEmitterHandler.class));
    planStatusEventEmitterHandler.getPlanExecutionSubject().register(
        injector.getInstance(Key.get(NotificationInformHandler.class)));

    PlanExecutionServiceImpl planExecutionService =
        (PlanExecutionServiceImpl) injector.getInstance(Key.get(PlanExecutionService.class));
    planExecutionService.getPlanStatusUpdateSubject().register(
        injector.getInstance(Key.get(ExecutionInfoUpdateEventHandler.class)));
    planExecutionService.getPlanStatusUpdateSubject().register(planStatusEventEmitterHandler);
    planExecutionService.getPlanStatusUpdateSubject().register(
        injector.getInstance(Key.get(PipelineStatusUpdateEventHandler.class)));
    planExecutionService.getPlanStatusUpdateSubject().register(
        injector.getInstance(Key.get(OrchestrationLogPublisher.class)));

    // Register PlanExecutionDeleteObserver
    planExecutionService.getPlanExecutionDeleteObserverSubject().register(
        injector.getInstance(Key.get(PlanExecutionMetadataDeleteObserver.class)));
    // Register PlanExecutionDeleteObserver
    planExecutionService.getPlanExecutionDeleteObserverSubject().register(
        injector.getInstance(Key.get(PipelineExecutionSummaryDeleteObserver.class)));
    // Register ResourceRestraintInstanceDeleteObserver
    planExecutionService.getPlanExecutionDeleteObserverSubject().register(
        injector.getInstance(Key.get(PipelineResourceRestraintInstanceDeleteObserver.class)));

    PlanExecutionStrategy planExecutionStrategy = injector.getInstance(Key.get(PlanExecutionStrategy.class));
    // StartObservers
    planExecutionStrategy.getOrchestrationStartSubject().register(
        injector.getInstance(Key.get(BarrierInitializer.class)));
    planExecutionStrategy.getOrchestrationStartSubject().register(
        injector.getInstance(Key.get(ResourceRestraintInitializer.class)));
    planExecutionStrategy.getOrchestrationStartSubject().register(
        injector.getInstance(Key.get(OrchestrationStartEventHandler.class)));
    planExecutionStrategy.getOrchestrationStartSubject().register(
        injector.getInstance(Key.get(ExecutionSummaryCreateEventHandler.class)));
    // End Observers
    planExecutionStrategy.getOrchestrationEndSubject().register(
        injector.getInstance(Key.get(OrchestrationEndGraphHandler.class)));
    planExecutionStrategy.getOrchestrationEndSubject().register(
        injector.getInstance(Key.get(OrchestrationEndInterruptHandler.class)));
    planExecutionStrategy.getOrchestrationEndSubject().register(
        injector.getInstance(Key.get(NotificationInformHandler.class)));
    planExecutionStrategy.getOrchestrationEndSubject().register(
        injector.getInstance(Key.get(InstrumentationPipelineEndEventHandler.class)));
    planExecutionStrategy.getOrchestrationEndSubject().register(
        injector.getInstance(Key.get(OrchestrationEndTagsResolveHandler.class)));
    planExecutionStrategy.getOrchestrationEndSubject().register(
        injector.getInstance(Key.get(PipelineStatusUpdateEventHandler.class)));
    planExecutionStrategy.getOrchestrationEndSubject().register(
        injector.getInstance(Key.get(ResourceRestraintObserver.class)));

    HMongoTemplate mongoTemplate = (HMongoTemplate) injector.getInstance(MongoTemplate.class);
    mongoTemplate.getTracerSubject().register(injector.getInstance(MongoRedisTracer.class));
  }

  private void registerCorrelationFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CorrelationFilter.class));
  }

  private void registerTraceFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(TraceFilter.class));
  }

  private void registerAuthFilters(PipelineServiceConfiguration config, Environment environment, Injector injector) {
    Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate = resourceInfoAndRequest
        -> (resourceInfoAndRequest.getKey().getResourceMethod() != null
               && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(PipelineServiceAuth.class) != null)
        || (resourceInfoAndRequest.getKey().getResourceClass() != null
            && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(PipelineServiceAuth.class) != null)
        || (resourceInfoAndRequest.getKey().getResourceMethod() != null
            && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(PipelineServiceAuthIfHasApiKey.class)
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
    environment.jersey().register(new NextGenAuthenticationFilter(predicate, null, serviceToSecretMapping,
        injector.getInstance(Key.get(TokenClient.class, Names.named("PRIVILEGED")))));
    registerInternalApiAuthFilter(config, environment);
  }

  private void registerInternalApiAuthFilter(PipelineServiceConfiguration configuration, Environment environment) {
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(DEFAULT.getServiceId(), configuration.getPipelineServiceSecret());
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
  /**------------------API auth telemetry -----------------------------------------------*/
  private void registerAPIAuthTelemetryFilters(
      PipelineServiceConfiguration configuration, Environment environment, Injector injector) {
    if (configuration.getSegmentConfiguration() != null && configuration.getSegmentConfiguration().isEnabled()) {
      registerAPIAuthTelemetryFilter(environment, injector);
      registerTerraformTelemetryFilter(environment, injector);
      registerAPIAuthTelemetryResponseFilter(environment, injector);
      registerAPIErrorsTelemetrySenderFilter(environment, injector);
    }
  }

  private void registerAPIAuthTelemetryFilter(Environment environment, Injector injector) {
    TelemetryReporter telemetryReporter = injector.getInstance(TelemetryReporter.class);
    environment.jersey().register(new APIAuthTelemetryFilter(telemetryReporter));
  }

  private void registerApiResponseFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(ApiResponseFilter.class));
  }

  private void registerTerraformTelemetryFilter(Environment environment, Injector injector) {
    TelemetryReporter telemetryReporter = injector.getInstance(TelemetryReporter.class);
    environment.jersey().register(new TerraformTelemetryFilter(telemetryReporter));
  }

  private void registerAPIAuthTelemetryResponseFilter(Environment environment, Injector injector) {
    TelemetryReporter telemetryReporter = injector.getInstance(TelemetryReporter.class);
    environment.jersey().register(new APIAuthTelemetryResponseFilter(telemetryReporter));
  }

  private void registerAPIErrorsTelemetrySenderFilter(Environment environment, Injector injector) {
    TelemetryReporter telemetryReporter = injector.getInstance(TelemetryReporter.class);
    environment.jersey().register(
        new APIErrorsTelemetrySenderFilter(telemetryReporter, PIPELINE_SERVICE.getServiceId()));
  }

  /**------------------Health Check -----------------------------------------------*/
  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("PMS", healthService);
    healthService.registerMonitor((HealthMonitor) injector.getInstance(MongoTemplate.class));
  }

  /**------------------Pms Sdk --------------------------------------------------*/

  private void registerPmsSdk(PipelineServiceConfiguration config, Injector injector) {
    PmsSdkConfiguration sdkConfig = getPmsSdkConfiguration(config);
    try {
      PmsSdkInitHelper.initializeSDKInstance(injector, sdkConfig);
    } catch (Exception ex) {
      throw new GeneralException("Failed to start pipeline service because pms sdk registration failed", ex);
    }
  }

  private PmsSdkConfiguration getPmsSdkConfiguration(PipelineServiceConfiguration config) {
    return PmsSdkConfiguration.builder()
        .deploymentMode(SdkDeployMode.REMOTE_IN_PROCESS)
        .moduleType(ModuleType.PMS)
        .pipelineServiceInfoProviderClass(PipelineServiceInternalInfoProvider.class)
        .filterCreationResponseMerger(new PipelineServiceFilterCreationResponseMerger())
        .engineSteps(PipelineServiceStepRegistrar.getEngineSteps())
        .engineFacilitators(PipelineServiceFacilitatorRegistrar.getEngineFacilitators())
        .engineAdvisers(PipelineServiceUtilAdviserRegistrar.getEngineAdvisers())
        .staticAliases(getStaticAliases())
        .jsonExpansionHandlers(getJsonExpansionHandlers())
        .engineEventHandlersMap(of())
        .executionSummaryModuleInfoProviderClass(PmsExecutionServiceInfoProvider.class)
        .eventsFrameworkConfiguration(config.getEventsFrameworkConfiguration())
        .executionPoolConfig(config.getPmsSdkExecutionPoolConfig())
        .orchestrationEventPoolConfig(config.getPmsSdkOrchestrationEventPoolConfig())
        .planCreatorServiceInternalConfig(config.getPmsPlanCreatorServicePoolConfig())
        .pipelineSdkRedisEventsConfig(config.getPipelineSdkRedisEventsConfig())
        .build();
  }

  @VisibleForTesting
  public Map<String, String> getStaticAliases() {
    Map<String, String> aliases = new HashMap<>();
    aliases.put(OrchestrationConstants.STAGE_SUCCESS,
        "<+stage.currentStatus> == \"SUCCEEDED\" || <+stage.currentStatus> == \"IGNORE_FAILED\"");
    aliases.put(OrchestrationConstants.STAGE_FAILURE,
        "<+stage.currentStatus> == \"FAILED\" || <+stage.currentStatus> == \"ERRORED\" || <+stage.currentStatus> == \"EXPIRED\"");
    aliases.put(OrchestrationConstants.PIPELINE_FAILURE,
        "<+pipeline.currentStatus> == \"FAILED\" || <+pipeline.currentStatus> == \"ERRORED\" || <+pipeline.currentStatus> == \"EXPIRED\"");
    aliases.put(OrchestrationConstants.PIPELINE_SUCCESS,
        "<+pipeline.currentStatus> == \"SUCCEEDED\" || <+pipeline.currentStatus> == \"IGNORE_FAILED\"");
    aliases.put(OrchestrationConstants.ALWAYS, "true");
    aliases.put(StrategyConstants.MATRIX, "strategy.matrix");
    aliases.put(StrategyConstants.REPEAT, "strategy.repeat");
    return aliases;
  }

  private List<JsonExpansionHandlerInfo> getJsonExpansionHandlers() {
    List<JsonExpansionHandlerInfo> jsonExpansionHandlers = new ArrayList<>();
    JsonExpansionInfo connectorRefExpansionInfo =
        JsonExpansionInfo.newBuilder().setKey(YAMLFieldNameConstants.CONNECTOR_REF).setExpansionType(KEY).build();
    JsonExpansionHandlerInfo connectorRefExpansionHandlerInfo =
        JsonExpansionHandlerInfo.builder()
            .jsonExpansionInfo(connectorRefExpansionInfo)
            .expansionHandler(DefaultConnectorRefExpansionHandler.class)
            .build();
    jsonExpansionHandlers.add(connectorRefExpansionHandlerInfo);
    return jsonExpansionHandlers;
  }

  private void registerEventListeners(Injector injector) {
    QueueListenerController queueListenerController = injector.getInstance(QueueListenerController.class);
    queueListenerController.register(injector.getInstance(DelayEventListener.class), 1);
    queueListenerController.register(injector.getInstance(PmsNotifyEventListener.class), 3);
  }

  private void registerWaitEnginePublishers(Injector injector) {
    final NotifyQueuePublisherRegister notifyQueuePublisherRegister =
        injector.getInstance(NotifyQueuePublisherRegister.class);
    notifyQueuePublisherRegister.register(PMS_ORCHESTRATION, injector.getInstance(PmsNotifyEventPublisher.class));
    notifyQueuePublisherRegister.register(PMS_PLAN_CREATION, injector.getInstance(PlanNotifyEventPublisher.class));
  }

  private void registerPmsSdkEvents(PipelineServiceConsumersConfig pipelineServiceConsumersConfig, Injector injector) {
    log.info("Initializing pms sdk redis abstract consumers...");
    PipelineEventConsumerController pipelineEventConsumerController =
        injector.getInstance(PipelineEventConsumerController.class);
    pipelineEventConsumerController.register(injector.getInstance(WebhookEventStreamConsumer.class),
        pipelineServiceConsumersConfig.getWebhookEvent().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(InterruptEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getInterrupt().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(OrchestrationEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getOrchestrationEvent().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(FacilitatorEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getFacilitatorEvent().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(NodeStartEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getNodeStart().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(ProgressEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getProgress().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(NodeAdviseEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getAdvise().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(NodeResumeEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getResume().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(SdkResponseEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getSdkResponse().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(GraphUpdateRedisConsumer.class),
        pipelineServiceConsumersConfig.getGraphUpdate().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(PipelineExecutionSummaryRedisEventConsumer.class),
        pipelineServiceConsumersConfig.getPipelineExecutionEvent().getThreads());
    pipelineEventConsumerController.register(
        injector.getInstance(PipelineExecutionSummaryRedisEventConsumerSnapshot.class),
        pipelineServiceConsumersConfig.getPipelineExecutionEventSnapshot().getThreads());
    //    pipelineEventConsumerController.register(injector.getInstance(PartialPlanResponseRedisConsumer.class),
    //        pipelineServiceConsumersConfig.getPartialPlanResponse().getThreads());
    //    pipelineEventConsumerController.register(injector.getInstance(CreatePartialPlanRedisConsumer.class),
    //        pipelineServiceConsumersConfig.getCreatePlan().getThreads());
    //    pipelineEventConsumerController.register(injector.getInstance(PlanNotifyEventConsumer.class),
    //        pipelineServiceConsumersConfig.getPlanNotify().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(PmsNotifyEventConsumerRedis.class),
        pipelineServiceConsumersConfig.getPmsNotify().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(InitiateNodeEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getInitiateNode().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(PollingEventStreamConsumer.class),
        pipelineServiceConsumersConfig.getPollingEvent().getThreads());
  }

  /**-----------------------------Git sync --------------------------------------*/
  private void registerGitSyncSdk(PipelineServiceConfiguration config, Injector injector, Environment environment) {
    GitSyncSdkConfiguration sdkConfig = getGitSyncConfiguration(config);
    try {
      GitSyncSdkInitHelper.initGitSyncSdk(injector, environment, sdkConfig);
    } catch (Exception ex) {
      throw new GeneralException("Failed to start pipeline service because git sync registration failed", ex);
    }
  }

  private GitSyncSdkConfiguration getGitSyncConfiguration(PipelineServiceConfiguration config) {
    final Supplier<List<EntityType>> sortOrder = () -> PMSGitEntityOrderComparator.sortOrder;
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    configureObjectMapper(objectMapper);
    Set<GitSyncEntitiesConfiguration> gitSyncEntitiesConfigurations = new HashSet<>();
    gitSyncEntitiesConfigurations.add(GitSyncEntitiesConfiguration.builder()
                                          .yamlClass(PipelineConfig.class)
                                          .entityClass(PipelineEntity.class)
                                          .entityType(EntityType.PIPELINES)
                                          .entityHelperClass(PipelineEntityGitSyncHelper.class)
                                          .build());
    gitSyncEntitiesConfigurations.add(GitSyncEntitiesConfiguration.builder()
                                          .yamlClass(InputSetYamlDTO.class)
                                          .entityClass(InputSetEntity.class)
                                          .entityType(EntityType.INPUT_SETS)
                                          .entityHelperClass(InputSetEntityGitSyncHelper.class)
                                          .build());
    final GitSdkConfiguration gitSdkConfiguration = config.getGitSdkConfiguration();
    return GitSyncSdkConfiguration.builder()
        .gitSyncSortOrder(sortOrder)
        .grpcClientConfig(gitSdkConfiguration.getGitManagerGrpcClientConfig())
        .grpcServerConfig(gitSdkConfiguration.getGitSdkGrpcServerConfig())
        .deployMode(GitSyncSdkConfiguration.DeployMode.REMOTE)
        .microservice(Microservice.PMS)
        .scmConnectionConfig(gitSdkConfiguration.getScmConnectionConfig())
        .eventsFrameworkConfiguration(config.getEventsFrameworkConfiguration())
        .serviceHeader(PIPELINE_SERVICE)
        .gitSyncEntitiesConfiguration(gitSyncEntitiesConfigurations)
        .gitSyncEntitySortComparator(PMSGitEntityOrderComparator.class)
        .objectMapper(objectMapper)
        .build();
  }

  private void registerScheduledJobs(Injector injector, PipelineServiceConfiguration appConfig) {
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateSyncServiceImpl.class), 0L,
            appConfig.getDelegatePollingConfig().getSyncDelay(), TimeUnit.MILLISECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateAsyncServiceImpl.class), 0L,
            appConfig.getDelegatePollingConfig().getAsyncDelay(), TimeUnit.MILLISECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateProgressServiceImpl.class), 0L,
            appConfig.getDelegatePollingConfig().getProgressDelay(), TimeUnit.MILLISECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("progressUpdateServiceExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(ProgressUpdateService.class), 0L,
            appConfig.getDelegatePollingConfig().getProgressDelay(), TimeUnit.MILLISECONDS);

    if (appConfig.isLockNotifyResponseCleanup()) {
      injector.getInstance(NotifierScheduledExecutorService.class)
          .scheduleWithFixedDelay(injector.getInstance(NotifyResponseCleanerSpringPersistence.class),
              random.nextInt(300), 300L, TimeUnit.SECONDS);
    } else {
      injector.getInstance(NotifierScheduledExecutorService.class)
          .scheduleWithFixedDelay(
              injector.getInstance(NotifyResponseCleaner.class), random.nextInt(300), 300L, TimeUnit.SECONDS);
    }
  }

  private void registerManagedBeans(Environment environment, Injector injector) {
    environment.lifecycle().manage(injector.getInstance(PMSEventConsumerService.class));
    environment.lifecycle().manage(injector.getInstance(QueueListenerController.class));
    environment.lifecycle().manage(injector.getInstance(ApprovalInstanceExpirationJob.class));
    environment.lifecycle().manage(injector.getInstance(OutboxEventPollService.class));
    environment.lifecycle().manage(injector.getInstance(PipelineEventConsumerController.class));
    // Do not remove as it's used for MaintenanceController for shutdown mode
    environment.lifecycle().manage(injector.getInstance(MaintenanceController.class));
  }

  private void registerCorsFilter(PipelineServiceConfiguration appConfig, Environment environment) {
    FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
    String allowedOrigins = String.join(",", appConfig.getAllowedOrigins());
    cors.setInitParameters(of("allowedOrigins", allowedOrigins, "allowedHeaders",
        "X-Requested-With,Content-Type,Accept,Origin,Authorization,X-api-key", "allowedMethods",
        "OPTIONS,GET,PUT,POST,DELETE,HEAD", "preflightMaxAge", "86400"));
    cors.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : HARNESS_RESOURCE_CLASSES) {
      environment.jersey().register(injector.getInstance(resource));
    }
    environment.jersey().register(injector.getInstance(VersionInfoResource.class));
  }

  private void registerJerseyProviders(Environment environment, Injector injector) {
    environment.jersey().register(JerseyViolationExceptionMapperV2.class);
    environment.jersey().register(GenericExceptionMapperV2.class);
    environment.jersey().register(new JsonProcessingExceptionMapper(true));
    environment.jersey().register(EarlyEofExceptionMapper.class);
    environment.jersey().register(NGAccessDeniedExceptionMapper.class);
    environment.jersey().register(WingsExceptionMapperV2.class);
    environment.jersey().register(NotFoundExceptionMapper.class);
    environment.jersey().register(NotAllowedExceptionMapper.class);

    environment.jersey().register(MultiPartFeature.class);
    //    environment.jersey().register(injector.getInstance(CharsetResponseFilter.class));
    //    environment.jersey().register(injector.getInstance(CorrelationFilter.class));
    //    environment.jersey().register(injector.getInstance(EtagFilter.class));
  }

  private void registerYamlSdk(Injector injector) {
    YamlSdkConfiguration yamlSdkConfiguration = YamlSdkConfiguration.builder()
                                                    .requireSchemaInit(true)
                                                    .requireSnippetInit(true)
                                                    .requireValidatorInit(false)
                                                    .build();
    YamlSdkInitHelper.initialize(injector, yamlSdkConfiguration);
  }

  private void registerNotificationTemplates(Injector injector) {
    ExecutorService executorService =
        injector.getInstance(Key.get(ExecutorService.class, Names.named("templateRegistrationExecutorService")));
    executorService.submit(injector.getInstance(NotificationTemplateRegistrar.class));
  }

  private void registerRequestContextFilter(Environment environment) {
    environment.jersey().register(new RequestContextFilter());
  }

  private void registerMigrations(Injector injector) {
    NGMigrationConfiguration config = getMigrationSdkConfiguration();
    NGMigrationSdkInitHelper.initialize(injector, config);
  }

  private NGMigrationConfiguration getMigrationSdkConfiguration() {
    return NGMigrationConfiguration.builder()
        .microservice(Microservice.PMS)
        .migrationProviderList(new ArrayList<Class<? extends MigrationProvider>>() {
          { add(PipelineCoreMigrationProvider.class); } // Add all migration provider classes here
        })
        .build();
  }

  private void initializeEnforcementSdk(Injector injector) {
    RestrictionUsageRegisterConfiguration restrictionUsageRegisterConfiguration =
        RestrictionUsageRegisterConfiguration.builder()
            .restrictionNameClassMap(
                ImmutableMap.<FeatureRestrictionName, Class<? extends RestrictionUsageInterface>>builder()
                    .put(FeatureRestrictionName.STRATEGY_MAX_CONCURRENT,
                        StrategyMaxConcurrencyRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MAX_PIPELINE_TIMEOUT_SECONDS, MaxStaticValueRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MAX_STAGE_TIMEOUT_SECONDS, MaxStaticValueRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MAX_STEP_TIMEOUT_SECONDS, MaxStaticValueRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MAX_CONCURRENT_ACTIVE_PIPELINE_EXECUTIONS,
                        MaxStaticValueRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MAX_PARALLEL_STEP_IN_A_PIPELINE,
                        MaxStaticValueRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.PIPELINE_EXECUTION_DATA_RETENTION_DAYS,
                        MaxStaticValueRestrictionUsageImpl.class)
                    .build())
            .build();
    CustomRestrictionRegisterConfiguration customConfig =
        CustomRestrictionRegisterConfiguration.builder()
            .customRestrictionMap(
                ImmutableMap.<FeatureRestrictionName, Class<? extends CustomRestrictionInterface>>builder().build())
            .build();

    injector.getInstance(EnforcementSdkRegisterService.class)
        .initialize(restrictionUsageRegisterConfiguration, customConfig);
  }
}
