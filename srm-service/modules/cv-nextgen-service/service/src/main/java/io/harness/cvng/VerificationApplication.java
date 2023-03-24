/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import static io.harness.NGConstants.X_API_KEY;
import static io.harness.authorization.AuthorizationServiceHeader.BEARER;
import static io.harness.authorization.AuthorizationServiceHeader.CV_NEXT_GEN;
import static io.harness.authorization.AuthorizationServiceHeader.DEFAULT;
import static io.harness.authorization.AuthorizationServiceHeader.IDENTITY_SERVICE;
import static io.harness.cvng.CVConstants.ENVIRONMENT;
import static io.harness.cvng.cdng.services.impl.CVNGNotifyEventListener.CVNG_ORCHESTRATION;
import static io.harness.cvng.migration.beans.CVNGSchema.CVNGMigrationStatus.RUNNING;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;
import static io.harness.security.ServiceTokenGenerator.VERIFICATION_SERVICE_SECRET;

import static com.google.inject.matcher.Matchers.not;
import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.AccessControlClientModule;
import io.harness.Microservice;
import io.harness.ModuleType;
import io.harness.PipelineServiceUtilityModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.CacheModule;
import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.controller.PrimaryVersionChangeScheduler;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.Activity.ActivityKeys;
import io.harness.cvng.activity.jobs.ActivityStatusJob;
import io.harness.cvng.activity.jobs.HarnessCDCurrentGenEventsHandler;
import io.harness.cvng.analysis.entities.VerificationTaskBase.VerificationTaskBaseKeys;
import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.cdng.jobs.CVNGStepTaskHandler;
import io.harness.cvng.cdng.services.impl.CVNGFilterCreationResponseMerger;
import io.harness.cvng.cdng.services.impl.CVNGModuleInfoProvider;
import io.harness.cvng.cdng.services.impl.CVNGNotifyEventListener;
import io.harness.cvng.cdng.services.impl.CVNGPipelineServiceInfoProvider;
import io.harness.cvng.client.ErrorTrackingClientModule;
import io.harness.cvng.client.NextGenClientModule;
import io.harness.cvng.client.VerificationManagerClientModule;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CVConfig.CVConfigKeys;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.DataCollectionTask.DataCollectionTaskKeys;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.entities.MonitoredService.MonitoredServiceKeys;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask.MonitoringSourcePerpetualTaskKeys;
import io.harness.cvng.core.entities.changeSource.ChangeSource;
import io.harness.cvng.core.entities.changeSource.ChangeSource.ChangeSourceKeys;
import io.harness.cvng.core.entities.changeSource.HarnessCDCurrentGenChangeSource;
import io.harness.cvng.core.entities.demo.CVNGDemoPerpetualTask;
import io.harness.cvng.core.entities.demo.CVNGDemoPerpetualTask.CVNGDemoPerpetualTaskKeys;
import io.harness.cvng.core.jobs.CVNGDemoPerpetualTaskHandler;
import io.harness.cvng.core.jobs.ChangeSourceDemoHandler;
import io.harness.cvng.core.jobs.CompositeSLODataExecutorTaskHandler;
import io.harness.cvng.core.jobs.CustomChangeEventConsumer;
import io.harness.cvng.core.jobs.DataCollectionTasksPerpetualTaskStatusUpdateHandler;
import io.harness.cvng.core.jobs.DeploymentChangeEventConsumer;
import io.harness.cvng.core.jobs.EntityCRUDStreamConsumer;
import io.harness.cvng.core.jobs.InternalChangeEventCEConsumer;
import io.harness.cvng.core.jobs.InternalChangeEventFFConsumer;
import io.harness.cvng.core.jobs.MonitoringSourcePerpetualTaskHandler;
import io.harness.cvng.core.jobs.PersistentLockCleanup;
import io.harness.cvng.core.jobs.SLIDataCollectionTaskCreateNextTaskHandler;
import io.harness.cvng.core.jobs.SLOHealthIndicatorTimescaleHandler;
import io.harness.cvng.core.jobs.SLOHistoryTimescaleHandler;
import io.harness.cvng.core.jobs.SLORecalculationFailureHandler;
import io.harness.cvng.core.jobs.ServiceGuardDataCollectionTaskCreateNextTaskHandler;
import io.harness.cvng.core.jobs.StatemachineEventConsumer;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.api.SideKickService;
import io.harness.cvng.exception.BadRequestExceptionMapper;
import io.harness.cvng.exception.ConstraintViolationExceptionMapper;
import io.harness.cvng.exception.NotFoundExceptionMapper;
import io.harness.cvng.exception.ServiceCallExceptionMapper;
import io.harness.cvng.governance.beans.ExpansionKeysConstants;
import io.harness.cvng.governance.services.SLOPolicyExpansionHandler;
import io.harness.cvng.licenserestriction.MaxServiceRestrictionUsageImpl;
import io.harness.cvng.metrics.services.impl.CVNGMetricsPublisher;
import io.harness.cvng.migration.CVNGSchemaHandler;
import io.harness.cvng.migration.SRMCoreMigrationProvider;
import io.harness.cvng.migration.beans.CVNGSchema;
import io.harness.cvng.migration.beans.CVNGSchema.CVNGSchemaKeys;
import io.harness.cvng.migration.service.CVNGMigrationService;
import io.harness.cvng.notification.jobs.MonitoredServiceNotificationHandler;
import io.harness.cvng.notification.jobs.SLONotificationHandler;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator.SLOHealthIndicatorKeys;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator.ServiceLevelIndicatorKeys;
import io.harness.cvng.statemachine.beans.AnalysisOrchestratorStatus;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator.AnalysisOrchestratorKeys;
import io.harness.cvng.statemachine.jobs.AnalysisOrchestrationJob;
import io.harness.cvng.utils.SRMServiceAuthIfHasApiKey;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.ExecutionStatus;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceKeys;
import io.harness.cvng.verificationjob.jobs.ProcessQueuedVerificationJobInstanceHandler;
import io.harness.cvng.verificationjob.jobs.VerificationJobInstanceTimeoutHandler;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.enforcement.client.CustomRestrictionRegisterConfiguration;
import io.harness.enforcement.client.RestrictionUsageRegisterConfiguration;
import io.harness.enforcement.client.custom.CustomRestrictionInterface;
import io.harness.enforcement.client.example.ExampleCustomImpl;
import io.harness.enforcement.client.example.ExampleRateLimitUsageImpl;
import io.harness.enforcement.client.example.ExampleStaticLimitUsageImpl;
import io.harness.enforcement.client.resources.EnforcementClientResource;
import io.harness.enforcement.client.services.EnforcementSdkRegisterService;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.exception.UnexpectedException;
import io.harness.ff.FeatureFlagConfig;
import io.harness.govern.ProviderModule;
import io.harness.health.HealthService;
import io.harness.iterator.PersistenceIterator;
import io.harness.licensing.usage.resources.LicenseUsageResource;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.metrics.MetricRegistryModule;
import io.harness.metrics.jobs.RecordMetricsJob;
import io.harness.metrics.service.api.MetricService;
import io.harness.migration.NGMigrationSdkInitHelper;
import io.harness.migration.NGMigrationSdkModule;
import io.harness.migration.beans.NGMigrationConfiguration;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.morphia.MorphiaModule;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.CorrelationFilter;
import io.harness.ng.core.TraceFilter;
import io.harness.ng.core.exceptionmappers.GenericExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.WingsExceptionMapperV2;
import io.harness.notification.Team;
import io.harness.notification.module.NotificationClientModule;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.templates.PredefinedTemplate;
import io.harness.outbox.OutboxEventPollService;
import io.harness.persistence.HPersistence;
import io.harness.persistence.UserProvider;
import io.harness.pms.contracts.plan.ExpansionRequestType;
import io.harness.pms.contracts.plan.JsonExpansionInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.events.base.PipelineEventConsumerController;
import io.harness.pms.sdk.PmsSdkConfiguration;
import io.harness.pms.sdk.PmsSdkInitHelper;
import io.harness.pms.sdk.PmsSdkModule;
import io.harness.pms.sdk.core.SdkDeployMode;
import io.harness.pms.sdk.core.governance.JsonExpansionHandlerInfo;
import io.harness.pms.sdk.execution.events.facilitators.FacilitatorEventRedisConsumer;
import io.harness.pms.sdk.execution.events.interrupts.InterruptEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.advise.NodeAdviseEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.resume.NodeResumeEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.start.NodeStartEventRedisConsumer;
import io.harness.pms.sdk.execution.events.orchestrationevent.OrchestrationEventRedisConsumer;
import io.harness.pms.sdk.execution.events.plan.CreatePartialPlanRedisConsumer;
import io.harness.pms.sdk.execution.events.progress.ProgressEventRedisConsumer;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.queue.QueueListenerController;
import io.harness.queue.QueuePublisher;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.resource.VersionInfoResource;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.security.NextGenAuthenticationFilter;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.serializer.CVNGStepRegistrar;
import io.harness.serializer.CvNextGenRegistrars;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.PipelineServiceUtilAdviserRegistrar;
import io.harness.serializer.PrimaryVersionManagerRegistrars;
import io.harness.token.TokenClientModule;
import io.harness.token.remote.TokenClient;
import io.harness.waiter.NotifyEvent;
import io.harness.waiter.NotifyQueuePublisherRegister;
import io.harness.waiter.ProgressUpdateService;
import io.harness.yaml.YamlSdkConfiguration;
import io.harness.yaml.YamlSdkInitHelper;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.palominolabs.metrics.guice.MetricsInstrumentationModule;
import dev.morphia.converters.TypeConverter;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import io.serializer.HObjectMapper;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.glassfish.jersey.server.model.Resource;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.springframework.core.convert.converter.Converter;
import ru.vyarus.guice.validator.ValidationModule;

@Slf4j
@OwnedBy(HarnessTeam.CV)
public class VerificationApplication extends Application<VerificationConfiguration> {
  private static String APPLICATION_NAME = "Verification NextGen Application";
  private static final SecureRandom random = new SecureRandom();

  private final MetricRegistry metricRegistry = new MetricRegistry();
  private HarnessMetricRegistry harnessMetricRegistry;
  private HPersistence hPersistence;

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));
    new VerificationApplication().run(args);
  }

  @Override
  public String getName() {
    return APPLICATION_NAME;
  }

  @Override
  public void initialize(Bootstrap<VerificationConfiguration> bootstrap) {
    initializeLogging();
    log.info("bootstrapping ...");
    // Enable variable substitution with environment variables
    bootstrap.addCommand(new InspectCommand<>(this));
    bootstrap.addCommand(new ScanClasspathMetadataCommand());
    bootstrap.addCommand(new GenerateOpenApiSpecCommand());
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    bootstrap.addBundle(new SwaggerBundle<VerificationConfiguration>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(
          VerificationConfiguration verificationServiceConfiguration) {
        return verificationServiceConfiguration.getSwaggerBundleConfiguration();
      }
    });
    bootstrap.setMetricRegistry(metricRegistry);
    configureObjectMapper(bootstrap.getObjectMapper());
    log.info("bootstrapping done.");
  }

  public static void configureObjectMapper(final ObjectMapper mapper) {
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    HObjectMapper.configureObjectMapperForNG(mapper);
  }

  private void createConsumerThreadsToListenToEvents(Injector injector) {
    new Thread(injector.getInstance(EntityCRUDStreamConsumer.class)).start();
    new Thread(injector.getInstance(DeploymentChangeEventConsumer.class)).start();
    new Thread(injector.getInstance(InternalChangeEventFFConsumer.class)).start();
    new Thread(injector.getInstance(InternalChangeEventCEConsumer.class)).start();
    new Thread(injector.getInstance(CustomChangeEventConsumer.class)).start();
    new Thread(injector.getInstance(StatemachineEventConsumer.class)).start();
  }

  private void scheduleMaintenanceActivities(Injector injector, VerificationConfiguration configuration) {
    ScheduledThreadPoolExecutor maintenanceActivitiesExecutor =
        new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("maintenance-activities").build());
    if (configuration.getDistributedLockImplementation() == DistributedLockImplementation.MONGO) {
      maintenanceActivitiesExecutor.scheduleWithFixedDelay(
          injector.getInstance(PersistentLockCleanup.class), random.nextInt(15), 15L, TimeUnit.MINUTES);
    }
  }

  @Override
  public void run(VerificationConfiguration configuration, Environment environment) {
    log.info("Starting app ...");
    MaintenanceController.forceMaintenance(true);
    ValidatorFactory validatorFactory = Validation.byDefaultProvider()
                                            .configure()
                                            .parameterNameProvider(new ReflectionParameterNameProvider())
                                            .buildValidatorFactory();

    List<Module> modules = new ArrayList<>();

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(CvNextGenRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(CvNextGenRegistrars.morphiaRegistrars)
            .addAll(PrimaryVersionManagerRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(CvNextGenRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder().build();
      }
    });

    modules.add(new AbstractCfModule() {
      @Override
      public CfClientConfig cfClientConfig() {
        return configuration.getCfClientConfig();
      }

      @Override
      public CfMigrationConfig cfMigrationConfig() {
        return CfMigrationConfig.builder().build();
      }

      @Override
      public FeatureFlagConfig featureFlagConfig() {
        return configuration.getFeatureFlagConfig();
      }
    });
    modules.add(MetricsInstrumentationModule.builder()
                    .withMetricRegistry(metricRegistry)
                    .withMatcher(not(new AbstractMatcher<TypeLiteral<?>>() {
                      @Override
                      public boolean matches(TypeLiteral<?> typeLiteral) {
                        return typeLiteral.getRawType().isAnnotationPresent(Path.class);
                      }
                    }))
                    .build());
    modules.add(new ValidationModule(validatorFactory));
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return configuration.getMongoConnectionFactory();
      }

      @Provides
      @Singleton
      @Named("dbAliases")
      public List<String> getDbAliases() {
        return configuration.getDbAliases();
      }
    });
    modules.add(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new UserPrincipalUserProvider();
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      @Named("morphiaClasses")
      Map<Class, String> morphiaCustomCollectionNames() {
        return ImmutableMap.<Class, String>builder()
            .put(DelegateSyncTaskResponse.class, "cvng_delegateSyncTaskResponses")
            .put(DelegateAsyncTaskResponse.class, "cvng_delegateAsyncTaskResponses")
            .put(DelegateTaskProgressResponse.class, "cvng_delegateTaskProgressResponses")
            .build();
      }
    });

    modules.add(MorphiaModule.getInstance());
    modules.add(AccessControlClientModule.getInstance(
        configuration.getAccessControlClientConfiguration(), CV_NEXT_GEN.getServiceId()));
    modules.add(new CVServiceModule(configuration));
    modules.add(new PersistentLockModule());
    modules.add(new EventsFrameworkModule(configuration.getEventsFrameworkConfiguration()));
    modules.add(new MetricRegistryModule(metricRegistry));
    modules.add(new VerificationManagerClientModule(configuration.getManagerClientConfig().getBaseUrl()));
    modules.add(new NextGenClientModule(configuration.getNgManagerServiceConfig()));
    modules.add(new ErrorTrackingClientModule(configuration.getErrorTrackingClientConfig()));
    modules.add(new SecretNGManagerClientModule(
        ServiceHttpClientConfig.builder().baseUrl(configuration.getNgManagerServiceConfig().getNgManagerUrl()).build(),
        configuration.getNgManagerServiceConfig().getManagerServiceSecret(), "NextGenManager"));
    modules.add(new CVNextGenCommonsServiceModule());
    modules.add(new NotificationClientModule(configuration.getNotificationClientConfiguration()));
    modules.add(new CvPersistenceModule());
    modules.add(new CacheModule(configuration.getCacheConfig()));
    modules.add(new TokenClientModule(
        ServiceHttpClientConfig.builder().baseUrl(configuration.getNgManagerServiceConfig().getNgManagerUrl()).build(),
        configuration.getNgManagerServiceConfig().getManagerServiceSecret(), "NextGenManager"));
    YamlSdkConfiguration yamlSdkConfiguration = YamlSdkConfiguration.builder()
                                                    .requireSchemaInit(true)
                                                    .requireSnippetInit(false)
                                                    .requireValidatorInit(false)
                                                    .build();

    // Pipeline Service Modules
    PmsSdkConfiguration pmsSdkConfiguration = getPmsSdkConfiguration(configuration);
    modules.add(PmsSdkModule.getInstance(pmsSdkConfiguration));
    modules.add(PipelineServiceUtilityModule.getInstance());
    modules.add(NGMigrationSdkModule.getInstance());
    Injector injector = Guice.createInjector(modules);
    YamlSdkInitHelper.initialize(injector, yamlSdkConfiguration);
    initializeServiceSecretKeys();
    harnessMetricRegistry = injector.getInstance(HarnessMetricRegistry.class);
    initMetrics(injector);
    autoCreateCollectionsAndIndexes(injector);
    registerCorrelationFilter(environment, injector);
    registerAuthFilters(environment, injector, configuration);
    registerManagedBeans(environment, injector);
    registerResources(environment, injector);
    registerVerificationTaskOrchestrationIterator(injector);
    registerVerificationJobInstanceDataCollectionTaskIterator(injector);
    registerDataCollectionTaskIterator(injector);
    registerCreateNextSLIDataCollectionTaskIterator(injector);
    registerCreateNextDataCollectionTaskIterator(injector);
    registerCVNGDemoPerpetualTaskIterator(injector);
    registerSLORecalculationFailure(injector);
    sloHistoryTimescale(injector);
    sloHealthIndicatorTimescale(injector);
    registerDataCollectionTasksPerpetualTaskStatusUpdateIterator(injector);
    registerCompositeSLODataExecutorTaskIterator(injector);
    injector.getInstance(CVNGStepTaskHandler.class).registerIterator();
    injector.getInstance(PrimaryVersionChangeScheduler.class).registerExecutors();
    registerExceptionMappers(environment.jersey());
    registerHealthChecks(environment, injector);
    createConsumerThreadsToListenToEvents(injector);
    registerCVNGSchemaMigrationIterator(injector);
    registerActivityIterator(injector);
    registerVerificationJobInstanceTimeoutIterator(injector);
    registerPipelineSDK(configuration, injector);
    registerWaitEnginePublishers(injector);
    registerPmsSdkEvents(injector);
    registerDemoGenerationIterator(injector);
    registerNotificationTemplates(configuration, injector);
    registerSLONotificationIterator(injector);
    registerMonitoredServiceNotificationIterator(injector);
    scheduleSidekickProcessing(injector);
    scheduleMaintenanceActivities(injector, configuration);
    initializeEnforcementSdk(injector);
    initAutoscalingMetrics();
    registerOasResource(configuration, environment, injector);
    registerMigrations(injector);

    if (BooleanUtils.isTrue(configuration.getEnableOpentelemetry())) {
      registerTraceFilter(environment, injector);
    }

    log.info("Leaving startup maintenance mode");
    MaintenanceController.forceMaintenance(false);
    registerUpdateProgressScheduler(injector);
    runMigrations(injector);
    log.info("Starting app done");
  }

  private void scheduleSidekickProcessing(Injector injector) {
    ScheduledThreadPoolExecutor workflowVerificationExecutor =
        new ScheduledThreadPoolExecutor(3, new ThreadFactoryBuilder().setNameFormat("side-kick").build());
    workflowVerificationExecutor.scheduleWithFixedDelay(
        () -> injector.getInstance(SideKickService.class).processNext(), 5, 5, TimeUnit.SECONDS);
  }

  private void registerUpdateProgressScheduler(Injector injector) {
    // This is need for wait notify update progress for CVNG step.
    ScheduledThreadPoolExecutor waitNotifyUpdateProgressExecutor =
        new ScheduledThreadPoolExecutor(2, new ThreadFactoryBuilder().setNameFormat("wait-notify-update").build());
    waitNotifyUpdateProgressExecutor.scheduleWithFixedDelay(
        injector.getInstance(ProgressUpdateService.class), 0L, 5L, TimeUnit.SECONDS);
  }

  private void registerQueueListeners(Injector injector) {
    log.info("Initializing queue listeners...");
    QueueListenerController queueListenerController = injector.getInstance(QueueListenerController.class);
    queueListenerController.register(injector.getInstance(CVNGNotifyEventListener.class), 1);
  }

  private void registerWaitEnginePublishers(Injector injector) {
    final QueuePublisher<NotifyEvent> publisher =
        injector.getInstance(Key.get(new TypeLiteral<QueuePublisher<NotifyEvent>>() {}));
    final NotifyQueuePublisherRegister notifyQueuePublisherRegister =
        injector.getInstance(NotifyQueuePublisherRegister.class);
    notifyQueuePublisherRegister.register(
        CVNG_ORCHESTRATION, payload -> publisher.send(Arrays.asList(CVNG_ORCHESTRATION), payload));
  }

  private void registerMigrations(Injector injector) {
    NGMigrationConfiguration config = getMigrationSdkConfiguration();
    NGMigrationSdkInitHelper.initialize(injector, config);
  }

  private NGMigrationConfiguration getMigrationSdkConfiguration() {
    return NGMigrationConfiguration.builder()
        .microservice(Microservice.CV)
        .migrationProviderList(new ArrayList<>() {
          { add(SRMCoreMigrationProvider.class); }
        })
        .build();
  }

  public void registerPipelineSDK(VerificationConfiguration configuration, Injector injector) {
    PmsSdkConfiguration sdkConfig = getPmsSdkConfiguration(configuration);
    if (sdkConfig.getDeploymentMode().equals(SdkDeployMode.REMOTE)) {
      try {
        PmsSdkInitHelper.initializeSDKInstance(injector, sdkConfig);
        if (configuration.getShouldConfigureWithPMS()) {
          registerQueueListeners(injector);
        }
      } catch (Exception e) {
        log.error("Failed To register pipeline sdk", e);
        // Don't fail for now. We have to find out retry strategy
        System.exit(1);
      }
    }
  }

  private PmsSdkConfiguration getPmsSdkConfiguration(VerificationConfiguration config) {
    boolean remote = false;
    if (config.getShouldConfigureWithPMS() != null && config.getShouldConfigureWithPMS()) {
      remote = true;
    }

    return PmsSdkConfiguration.builder()
        .deploymentMode(remote ? SdkDeployMode.REMOTE : SdkDeployMode.LOCAL)
        .moduleType(ModuleType.CV)
        .pipelineServiceInfoProviderClass(CVNGPipelineServiceInfoProvider.class)
        .grpcServerConfig(config.getPmsSdkGrpcServerConfig())
        .pmsGrpcClientConfig(config.getPmsGrpcClientConfig())
        .engineSteps(CVNGStepRegistrar.getEngineSteps())
        .engineAdvisers(PipelineServiceUtilAdviserRegistrar.getEngineAdvisers())
        .engineFacilitators(new HashMap<>())
        .filterCreationResponseMerger(new CVNGFilterCreationResponseMerger())
        .executionSummaryModuleInfoProviderClass(CVNGModuleInfoProvider.class)
        .eventsFrameworkConfiguration(config.getEventsFrameworkConfiguration())
        .jsonExpansionHandlers(getJsonExpansionHandlers())
        .build();
  }

  private List<JsonExpansionHandlerInfo> getJsonExpansionHandlers() {
    List<JsonExpansionHandlerInfo> jsonExpansionHandlers = new ArrayList<>();
    JsonExpansionInfo sloPolicyInfo =
        JsonExpansionInfo.newBuilder()
            .setExpansionType(ExpansionRequestType.LOCAL_FQN)
            .setKey(YAMLFieldNameConstants.STAGE)
            .setExpansionKey(ExpansionKeysConstants.SLO_POLICY_EXPANSION_KEY)
            .setStageType(StepType.newBuilder().setType("Deployment").setStepCategory(StepCategory.STAGE).build())
            .build();
    JsonExpansionHandlerInfo sloPolicyHandler = JsonExpansionHandlerInfo.builder()
                                                    .jsonExpansionInfo(sloPolicyInfo)
                                                    .expansionHandler(SLOPolicyExpansionHandler.class)
                                                    .build();
    jsonExpansionHandlers.add(sloPolicyHandler);
    return jsonExpansionHandlers;
  }

  private void initMetrics(Injector injector) {
    injector.getInstance(MetricService.class)
        .initializeMetrics(Arrays.asList(injector.getInstance(CVNGMetricsPublisher.class)));
    injector.getInstance(RecordMetricsJob.class).scheduleMetricsTasks();
  }

  private void autoCreateCollectionsAndIndexes(Injector injector) {
    hPersistence = injector.getInstance(HPersistence.class);
  }

  private void registerOasResource(
      VerificationConfiguration verificationConfiguration, Environment environment, Injector injector) {
    OpenApiResource openApiResource = injector.getInstance(OpenApiResource.class);
    openApiResource.setOpenApiConfiguration(verificationConfiguration.getOasConfig());
    environment.jersey().register(openApiResource);
  }

  private void registerActivityIterator(Injector injector) {
    ScheduledThreadPoolExecutor workflowVerificationExecutor =
        new ScheduledThreadPoolExecutor(5, new ThreadFactoryBuilder().setNameFormat("Iterator-Activity").build());
    Handler<Activity> handler = injector.getInstance(ActivityStatusJob.class);
    PersistenceIterator activityIterator =
        MongoPersistenceIterator.<Activity, MorphiaFilterExpander<Activity>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("ActivityIterator")
            .clazz(Activity.class)
            .fieldName(ActivityKeys.verificationIteration)
            .targetInterval(ofSeconds(30))
            .acceptableNoAlertDelay(ofSeconds(15))
            .executorService(workflowVerificationExecutor)
            .semaphore(new Semaphore(7))
            .handler(handler)
            .schedulingType(REGULAR)
            .filterExpander(query
                -> query.field(ActivityKeys.verificationJobInstanceIds)
                       .exists()
                       .field(ActivityKeys.analysisStatus)
                       .in(Lists.newArrayList(
                           ActivityVerificationStatus.NOT_STARTED, ActivityVerificationStatus.IN_PROGRESS)))
            .redistribute(true)
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .build();
    injector.injectMembers(activityIterator);
    workflowVerificationExecutor.scheduleWithFixedDelay(() -> activityIterator.process(), 0, 30, TimeUnit.SECONDS);
  }

  private void registerVerificationTaskOrchestrationIterator(Injector injector) {
    // TODO: Reevaluate the thread count here. 20 might be enough now but as we scale, we need to reconsider.
    int poolSize = 20;
    ScheduledThreadPoolExecutor workflowVerificationExecutor = new ScheduledThreadPoolExecutor(
        poolSize, new ThreadFactoryBuilder().setNameFormat("Iterator-Analysis").build());
    Handler<AnalysisOrchestrator> handler = injector.getInstance(AnalysisOrchestrationJob.class);

    PersistenceIterator analysisOrchestrationIterator =
        MongoPersistenceIterator.<AnalysisOrchestrator, MorphiaFilterExpander<AnalysisOrchestrator>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("AnalysisOrchestrationIterator")
            .clazz(AnalysisOrchestrator.class)
            .fieldName(AnalysisOrchestratorKeys.analysisOrchestrationIteration)
            .targetInterval(ofSeconds(15))
            .acceptableNoAlertDelay(ofSeconds(30))
            .executorService(workflowVerificationExecutor)
            .semaphore(new Semaphore(poolSize - 1))
            .handler(handler)
            .schedulingType(REGULAR)
            .filterExpander(query
                -> query.field(AnalysisOrchestratorKeys.status)
                       .in(Lists.newArrayList(AnalysisOrchestratorStatus.CREATED, AnalysisOrchestratorStatus.RUNNING)))
            .redistribute(true)
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .build();
    injector.injectMembers(analysisOrchestrationIterator);
    workflowVerificationExecutor.scheduleWithFixedDelay(
        () -> analysisOrchestrationIterator.process(), 0, 5, TimeUnit.SECONDS);
  }

  private void registerVerificationJobInstanceTimeoutIterator(Injector injector) {
    ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(
        2, new ThreadFactoryBuilder().setNameFormat("verificationJobInstance-timeout-iterator").build());
    Handler<VerificationJobInstance> handler = injector.getInstance(VerificationJobInstanceTimeoutHandler.class);
    PersistenceIterator persistenceIterator =
        MongoPersistenceIterator.<VerificationJobInstance, MorphiaFilterExpander<VerificationJobInstance>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("TimeoutTaskIterator")
            .clazz(VerificationJobInstance.class)
            .fieldName(VerificationJobInstanceKeys.timeoutTaskIteration)
            .targetInterval(ofMinutes(1))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(scheduledThreadPoolExecutor)
            .semaphore(new Semaphore(1))
            .handler(handler)
            .schedulingType(REGULAR)
            .filterExpander(query
                -> query.field(VerificationJobInstanceKeys.executionStatus).in(ExecutionStatus.nonFinalStatuses()))
            .redistribute(true)
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .build();
    injector.injectMembers(persistenceIterator);
    scheduledThreadPoolExecutor.scheduleWithFixedDelay(() -> persistenceIterator.process(), 0, 1, TimeUnit.MINUTES);
  }

  private void registerDemoGenerationIterator(Injector injector) {
    ScheduledThreadPoolExecutor dataCollectionExecutor = new ScheduledThreadPoolExecutor(
        2, new ThreadFactoryBuilder().setNameFormat("demo-generator-change-source").build());
    ChangeSourceDemoHandler changeSourceDemoHandler = injector.getInstance(ChangeSourceDemoHandler.class);
    PersistenceIterator demoDataIterator =
        MongoPersistenceIterator.<ChangeSource, MorphiaFilterExpander<ChangeSource>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("DemoGenerationIterator")
            .clazz(ChangeSource.class)
            .fieldName(ChangeSourceKeys.demoDataGenerationIteration)
            .targetInterval(ofMinutes(80))
            .acceptableNoAlertDelay(ofMinutes(10))
            .executorService(dataCollectionExecutor)
            .semaphore(new Semaphore(2))
            .handler(changeSourceDemoHandler)
            .schedulingType(REGULAR)
            .filterExpander(query -> query.filter(ChangeSourceKeys.isConfiguredForDemo, true))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(demoDataIterator);
    dataCollectionExecutor.scheduleWithFixedDelay(() -> demoDataIterator.process(), 0, 30, TimeUnit.SECONDS);
  }

  private void registerDataCollectionTaskIterator(Injector injector) {
    ScheduledThreadPoolExecutor dataCollectionExecutor = new ScheduledThreadPoolExecutor(
        5, new ThreadFactoryBuilder().setNameFormat("cv-config-data-collection-iterator").build());

    MonitoringSourcePerpetualTaskHandler monitoringSourcePerpetualTaskHandler =
        injector.getInstance(MonitoringSourcePerpetualTaskHandler.class);
    PersistenceIterator monitoringSourceIterator =
        MongoPersistenceIterator
            .<MonitoringSourcePerpetualTask, MorphiaFilterExpander<MonitoringSourcePerpetualTask>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("MonitoringSourceIterator")
            .clazz(MonitoringSourcePerpetualTask.class)
            .fieldName(MonitoringSourcePerpetualTaskKeys.dataCollectionTaskIteration)
            .targetInterval(ofMinutes(5))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(dataCollectionExecutor)
            .semaphore(new Semaphore(5))
            .handler(monitoringSourcePerpetualTaskHandler)
            .schedulingType(REGULAR)
            .filterExpander(query -> query.criteria(MonitoringSourcePerpetualTaskKeys.perpetualTaskId).doesNotExist())
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(monitoringSourceIterator);
    dataCollectionExecutor.scheduleWithFixedDelay(() -> monitoringSourceIterator.process(), 0, 30, TimeUnit.SECONDS);

    HarnessCDCurrentGenEventsHandler harnessCDCurrentGenEventsHandler =
        injector.getInstance(HarnessCDCurrentGenEventsHandler.class);
    PersistenceIterator harnessCDIterator =
        MongoPersistenceIterator
            .<HarnessCDCurrentGenChangeSource, MorphiaFilterExpander<HarnessCDCurrentGenChangeSource>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("HarnessCDIterator")
            .clazz(HarnessCDCurrentGenChangeSource.class)
            .fieldName(ChangeSourceKeys.dataCollectionTaskIteration)
            .targetInterval(ofMinutes(1))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(dataCollectionExecutor)
            .semaphore(new Semaphore(5))
            .handler(harnessCDCurrentGenEventsHandler)
            .schedulingType(REGULAR)
            .filterExpander(query -> query.filter(ChangeSourceKeys.type, ChangeSourceType.HARNESS_CD_CURRENT_GEN))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(harnessCDIterator);
    dataCollectionExecutor.scheduleWithFixedDelay(() -> harnessCDIterator.process(), 0, 30, TimeUnit.SECONDS);
  }

  private void registerCreateNextDataCollectionTaskIterator(Injector injector) {
    ScheduledThreadPoolExecutor dataCollectionExecutor = new ScheduledThreadPoolExecutor(
        3, new ThreadFactoryBuilder().setNameFormat("create-next-task-iterator").build());
    ServiceGuardDataCollectionTaskCreateNextTaskHandler serviceGuardDataCollectionTaskCreateNextTaskHandler =
        injector.getInstance(ServiceGuardDataCollectionTaskCreateNextTaskHandler.class);
    PersistenceIterator liveMonitoringDataCollectionTaskRecoverHandlerIterator =
        MongoPersistenceIterator.<CVConfig, MorphiaFilterExpander<CVConfig>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("CreateNextDataCollectionTaskIterator")
            .clazz(CVConfig.class)
            .fieldName(CVConfigKeys.createNextTaskIteration)
            .targetInterval(ofMinutes(5))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(dataCollectionExecutor)
            .semaphore(new Semaphore(3))
            .handler(serviceGuardDataCollectionTaskCreateNextTaskHandler)
            .schedulingType(REGULAR)
            .filterExpander(query -> query.filter(CVConfigKeys.enabled, true))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(liveMonitoringDataCollectionTaskRecoverHandlerIterator);
    dataCollectionExecutor.scheduleWithFixedDelay(
        () -> liveMonitoringDataCollectionTaskRecoverHandlerIterator.process(), 0, 1, TimeUnit.MINUTES);
  }

  private void registerCreateNextSLIDataCollectionTaskIterator(Injector injector) {
    ScheduledThreadPoolExecutor dataCollectionExecutor = new ScheduledThreadPoolExecutor(
        3, new ThreadFactoryBuilder().setNameFormat("create-next-sli-task-iterator").build());
    SLIDataCollectionTaskCreateNextTaskHandler sliDataCollectionTaskCreateNextTaskHandler =
        injector.getInstance(SLIDataCollectionTaskCreateNextTaskHandler.class);
    PersistenceIterator sliDataCollectionTaskRecoverHandlerIterator =
        MongoPersistenceIterator.<ServiceLevelIndicator, MorphiaFilterExpander<ServiceLevelIndicator>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("CreateNextSLIDataCollectionTaskIterator")
            .clazz(ServiceLevelIndicator.class)
            .fieldName(ServiceLevelIndicatorKeys.createNextTaskIteration)
            .targetInterval(ofMinutes(5))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(dataCollectionExecutor)
            .semaphore(new Semaphore(3))
            .handler(sliDataCollectionTaskCreateNextTaskHandler)
            .schedulingType(REGULAR)
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(sliDataCollectionTaskRecoverHandlerIterator);
    dataCollectionExecutor.scheduleWithFixedDelay(
        () -> sliDataCollectionTaskRecoverHandlerIterator.process(), 0, 1, TimeUnit.MINUTES);
  }

  private void registerSLORecalculationFailure(Injector injector) {
    ScheduledThreadPoolExecutor dataCollectionExecutor = new ScheduledThreadPoolExecutor(
        3, new ThreadFactoryBuilder().setNameFormat("slo-recalculation-failure").build());
    SLORecalculationFailureHandler sloRecalculationFailureHandler =
        injector.getInstance(SLORecalculationFailureHandler.class);
    PersistenceIterator sloRecalculationFailureHandlerIterator =
        MongoPersistenceIterator
            .<AbstractServiceLevelObjective, MorphiaFilterExpander<AbstractServiceLevelObjective>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("SloRecalculationFailureHandlerIterator")
            .clazz(AbstractServiceLevelObjective.class)
            .fieldName(ServiceLevelObjectiveV2Keys.recordMetricIteration)
            .targetInterval(ofMinutes(5))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(dataCollectionExecutor)
            .semaphore(new Semaphore(3))
            .handler(sloRecalculationFailureHandler)
            .schedulingType(REGULAR)
            .filterExpander(query
                -> query.criteria(ServiceLevelObjectiveV2Keys.lastUpdatedAt)
                       .greaterThan(
                           injector.getInstance(Clock.class).instant().minus(45, ChronoUnit.MINUTES).toEpochMilli()))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(sloRecalculationFailureHandlerIterator);
    dataCollectionExecutor.scheduleWithFixedDelay(
        () -> sloRecalculationFailureHandlerIterator.process(), 0, 1, TimeUnit.MINUTES);
  }
  private void sloHistoryTimescale(Injector injector) {
    ScheduledThreadPoolExecutor dataCollectionExecutor =
        new ScheduledThreadPoolExecutor(3, new ThreadFactoryBuilder().setNameFormat("slo-history-timescale").build());
    SLOHistoryTimescaleHandler sloHistoryTimescaleHandler = injector.getInstance(SLOHistoryTimescaleHandler.class);
    PersistenceIterator sloHistoryTimescaleHandlerIterator =
        MongoPersistenceIterator
            .<AbstractServiceLevelObjective, MorphiaFilterExpander<AbstractServiceLevelObjective>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("SLOHistoryTimescaleHandlerIterator")
            .clazz(AbstractServiceLevelObjective.class)
            .fieldName(ServiceLevelObjectiveV2Keys.sloHistoryTimescaleIteration)
            .targetInterval(ofHours(24))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(dataCollectionExecutor)
            .semaphore(new Semaphore(3))
            .handler(sloHistoryTimescaleHandler)
            .schedulingType(REGULAR)
            .filterExpander(query
                -> query.criteria(ServiceLevelObjectiveV2Keys.lastUpdatedAt)
                       .greaterThan(
                           injector.getInstance(Clock.class).instant().minus(45, ChronoUnit.MINUTES).toEpochMilli()))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(sloHistoryTimescaleHandlerIterator);
    dataCollectionExecutor.scheduleWithFixedDelay(
        () -> sloHistoryTimescaleHandlerIterator.process(), 0, 1, TimeUnit.MINUTES);
  }

  private void sloHealthIndicatorTimescale(Injector injector) {
    ScheduledThreadPoolExecutor dataCollectionExecutor = new ScheduledThreadPoolExecutor(
        3, new ThreadFactoryBuilder().setNameFormat("slo-health-indicator-timescale").build());
    SLOHealthIndicatorTimescaleHandler sloHealthIndicatorTimescaleHandler =
        injector.getInstance(SLOHealthIndicatorTimescaleHandler.class);
    PersistenceIterator sloHealthIndicatorTimescaleHandlerIterator =
        MongoPersistenceIterator.<SLOHealthIndicator, MorphiaFilterExpander<SLOHealthIndicator>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("SLOHealthIndicatorTimescaleHandlerIterator")
            .clazz(SLOHealthIndicator.class)
            .fieldName(SLOHealthIndicatorKeys.timescaleIteration)
            .targetInterval(ofMinutes(60))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(dataCollectionExecutor)
            .semaphore(new Semaphore(3))
            .handler(sloHealthIndicatorTimescaleHandler)
            .schedulingType(REGULAR)
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(sloHealthIndicatorTimescaleHandler);
    dataCollectionExecutor.scheduleWithFixedDelay(
        () -> sloHealthIndicatorTimescaleHandlerIterator.process(), 0, 60, TimeUnit.MINUTES);
  }

  private void registerCVNGDemoPerpetualTaskIterator(Injector injector) {
    ScheduledThreadPoolExecutor cvngDemoPerpetualTaskExecutor = new ScheduledThreadPoolExecutor(
        3, new ThreadFactoryBuilder().setNameFormat("create-cvng-perpetual-task-iterator").build());

    CVNGDemoPerpetualTaskHandler cvngDemoPerpetualTaskHandler =
        injector.getInstance(CVNGDemoPerpetualTaskHandler.class);

    PersistenceIterator cvngDemoPerpetualTaskIterator =
        MongoPersistenceIterator.<CVNGDemoPerpetualTask, MorphiaFilterExpander<CVNGDemoPerpetualTask>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("CVNGDemoPerpetualTaskIterator")
            .clazz(CVNGDemoPerpetualTask.class)
            .fieldName(CVNGDemoPerpetualTaskKeys.createNextTaskIteration)
            .targetInterval(ofMinutes(1))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(cvngDemoPerpetualTaskExecutor)
            .semaphore(new Semaphore(3))
            .handler(cvngDemoPerpetualTaskHandler)
            .schedulingType(REGULAR)
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();

    injector.injectMembers(cvngDemoPerpetualTaskIterator);
    cvngDemoPerpetualTaskExecutor.scheduleWithFixedDelay(
        () -> cvngDemoPerpetualTaskIterator.process(), 0, 1, TimeUnit.MINUTES);
  }

  private void registerDataCollectionTasksPerpetualTaskStatusUpdateIterator(Injector injector) {
    ScheduledThreadPoolExecutor dataCollectionTasksPerpetualTaskStatusUpdateExecutor =
        new ScheduledThreadPoolExecutor(3,
            new ThreadFactoryBuilder()
                .setNameFormat("create-data-collection-tasks-perpetual-task-status-update-iterator")
                .build());

    DataCollectionTasksPerpetualTaskStatusUpdateHandler dataCollectionTasksPerpetualTaskStatusUpdateHandler =
        injector.getInstance(DataCollectionTasksPerpetualTaskStatusUpdateHandler.class);

    PersistenceIterator dataCollectionTasksPerpetualTaskStatusUpdateIterator =
        MongoPersistenceIterator.<DataCollectionTask, MorphiaFilterExpander<DataCollectionTask>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("DataCollectionTasksPerpetualTaskStatusUpdateIterator")
            .clazz(DataCollectionTask.class)
            .fieldName(DataCollectionTaskKeys.workerStatusIteration)
            .targetInterval(ofMinutes(1))
            .acceptableNoAlertDelay(ofMinutes(5))
            .executorService(dataCollectionTasksPerpetualTaskStatusUpdateExecutor)
            .semaphore(new Semaphore(2))
            .handler(dataCollectionTasksPerpetualTaskStatusUpdateHandler)
            .schedulingType(REGULAR)
            .filterExpander(query
                -> query.and(
                    query.or(query.criteria(DataCollectionTaskKeys.status).equal(DataCollectionExecutionStatus.QUEUED),
                        query.and(
                            query.criteria(DataCollectionTaskKeys.status).equal(DataCollectionExecutionStatus.RUNNING),
                            query.criteria(VerificationTaskBaseKeys.lastUpdatedAt)
                                .lessThan(injector.getInstance(Clock.class)
                                              .instant()
                                              .minus(5, ChronoUnit.MINUTES)
                                              .toEpochMilli()))),
                    query.criteria(DataCollectionTaskKeys.validAfter)
                        .lessThan(injector.getInstance(Clock.class).instant().minus(3, ChronoUnit.MINUTES))))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();

    injector.injectMembers(dataCollectionTasksPerpetualTaskStatusUpdateIterator);
    dataCollectionTasksPerpetualTaskStatusUpdateExecutor.scheduleWithFixedDelay(
        () -> dataCollectionTasksPerpetualTaskStatusUpdateIterator.process(), 0, 2, TimeUnit.MINUTES);
  }

  private void registerCompositeSLODataExecutorTaskIterator(Injector injector) {
    ScheduledThreadPoolExecutor compositeSLODataExecutorTaskExecutor = new ScheduledThreadPoolExecutor(
        3, new ThreadFactoryBuilder().setNameFormat("composite-slo-data-collection-task-iterator").build());

    CompositeSLODataExecutorTaskHandler compositeSLODataExecutorTaskHandler =
        injector.getInstance(CompositeSLODataExecutorTaskHandler.class);

    PersistenceIterator serviceLevelObjectiveV2CompositeSLOTaskIterator =
        MongoPersistenceIterator
            .<AbstractServiceLevelObjective, MorphiaFilterExpander<AbstractServiceLevelObjective>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("ServiceLevelObjectiveV2CompositeSLOTaskIterator")
            .clazz(AbstractServiceLevelObjective.class)
            .fieldName(ServiceLevelObjectiveV2Keys.createNextTaskIteration)
            .targetInterval(ofMinutes(1))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(compositeSLODataExecutorTaskExecutor)
            .semaphore(new Semaphore(2))
            .handler(compositeSLODataExecutorTaskHandler)
            .schedulingType(REGULAR)
            .filterExpander(query
                -> query.and(
                    query.criteria(ServiceLevelObjectiveV2Keys.type).equal(ServiceLevelObjectiveType.COMPOSITE),
                    query.criteria(ServiceLevelObjectiveV2Keys.createNextTaskIteration).equal(0L)))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();

    injector.injectMembers(serviceLevelObjectiveV2CompositeSLOTaskIterator);
    compositeSLODataExecutorTaskExecutor.scheduleWithFixedDelay(
        () -> serviceLevelObjectiveV2CompositeSLOTaskIterator.process(), 0, 30, TimeUnit.SECONDS);
  }

  private void registerVerificationJobInstanceDataCollectionTaskIterator(Injector injector) {
    ScheduledThreadPoolExecutor verificationTaskExecutor = new ScheduledThreadPoolExecutor(
        5, new ThreadFactoryBuilder().setNameFormat("verification-job-instance-data-collection-iterator").build());
    ProcessQueuedVerificationJobInstanceHandler handler =
        injector.getInstance(ProcessQueuedVerificationJobInstanceHandler.class);
    // TODO: setup alert if this goes above acceptable threshold.
    PersistenceIterator dataCollectionIterator =
        MongoPersistenceIterator.<VerificationJobInstance, MorphiaFilterExpander<VerificationJobInstance>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("VerificationJobInstanceDataCollectionTaskIterator")
            .clazz(VerificationJobInstance.class)
            .fieldName(VerificationJobInstanceKeys.dataCollectionTaskIteration)
            .targetInterval(ofSeconds(30))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(verificationTaskExecutor)
            .semaphore(new Semaphore(5))
            .handler(handler)
            .schedulingType(REGULAR)
            // TODO: find a way to implement retry logic.
            .filterExpander(query -> query.filter(VerificationJobInstanceKeys.executionStatus, ExecutionStatus.QUEUED))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(dataCollectionIterator);
    verificationTaskExecutor.scheduleWithFixedDelay(() -> dataCollectionIterator.process(), 0, 30, TimeUnit.SECONDS);
  }

  private void registerHealthChecks(Environment environment, Injector injector) {
    HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("CV nextgen", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  private void registerAuthFilters(
      Environment environment, Injector injector, VerificationConfiguration configuration) {
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate = resourceInfoAndRequest
        -> resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(NextGenManagerAuth.class) != null
        || resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(NextGenManagerAuth.class) != null
        || (resourceInfoAndRequest.getKey().getResourceMethod() != null
            && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(SRMServiceAuthIfHasApiKey.class)
                != null
            && resourceInfoAndRequest.getValue().getHeaders().get(X_API_KEY) != null);
    serviceToSecretMapping.put(BEARER.getServiceId(), configuration.getManagerAuthConfig().getJwtAuthSecret());
    serviceToSecretMapping.put(
        IDENTITY_SERVICE.getServiceId(), configuration.getManagerAuthConfig().getJwtIdentityServiceSecret());
    serviceToSecretMapping.put(
        DEFAULT.getServiceId(), configuration.getNgManagerServiceConfig().getManagerServiceSecret());
    environment.jersey().register(new NextGenAuthenticationFilter(predicate, null, serviceToSecretMapping,
        injector.getInstance(Key.get(TokenClient.class, Names.named("PRIVILEGED")))));
    environment.jersey().register(injector.getInstance(CVNGAuthenticationFilter.class));
  }

  private void registerCVNGSchemaMigrationIterator(Injector injector) {
    ScheduledThreadPoolExecutor migrationExecutor = new ScheduledThreadPoolExecutor(
        2, new ThreadFactoryBuilder().setNameFormat("cvng-schema-migration-iterator").build());
    CVNGSchemaHandler cvngSchemaMigrationHandler = injector.getInstance(CVNGSchemaHandler.class);

    PersistenceIterator dataCollectionIterator =
        MongoPersistenceIterator.<CVNGSchema, MorphiaFilterExpander<CVNGSchema>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("CVNGSchemaMigrationIterator")
            .clazz(CVNGSchema.class)
            .fieldName(CVNGSchemaKeys.cvngNextIteration)
            .targetInterval(ofMinutes(30))
            .acceptableNoAlertDelay(ofMinutes(5))
            .executorService(migrationExecutor)
            .semaphore(new Semaphore(3))
            .handler(cvngSchemaMigrationHandler)
            .schedulingType(REGULAR)
            .filterExpander(query -> query.criteria(CVNGSchemaKeys.cvngMigrationStatus).notEqual(RUNNING))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(dataCollectionIterator);
    migrationExecutor.scheduleWithFixedDelay(() -> dataCollectionIterator.process(), 0, 15, TimeUnit.MINUTES);
  }

  private void registerNotificationTemplates(VerificationConfiguration configuration, Injector injector) {
    NotificationClient notificationClient = injector.getInstance(NotificationClient.class);
    List<PredefinedTemplate> templates = new ArrayList<>(
        Arrays.asList(PredefinedTemplate.CVNG_SLO_SIMPLE_SLACK, PredefinedTemplate.CVNG_SLO_SIMPLE_EMAIL,
            PredefinedTemplate.CVNG_SLO_SIMPLE_PAGERDUTY, PredefinedTemplate.CVNG_SLO_SIMPLE_MSTEAMS,
            PredefinedTemplate.CVNG_SLO_COMPOSITE_SLACK, PredefinedTemplate.CVNG_SLO_COMPOSITE_EMAIL,
            PredefinedTemplate.CVNG_SLO_COMPOSITE_PAGERDUTY, PredefinedTemplate.CVNG_SLO_COMPOSITE_MSTEAMS,
            PredefinedTemplate.CVNG_MONITOREDSERVICE_SLACK, PredefinedTemplate.CVNG_MONITOREDSERVICE_EMAIL,
            PredefinedTemplate.CVNG_MONITOREDSERVICE_PAGERDUTY, PredefinedTemplate.CVNG_MONITOREDSERVICE_MSTEAMS,
            PredefinedTemplate.CVNG_MONITOREDSERVICE_ET_SLACK, PredefinedTemplate.CVNG_MONITOREDSERVICE_ET_EMAIL));

    if (configuration.getShouldConfigureWithNotification()) {
      for (PredefinedTemplate template : templates) {
        try {
          log.info("Registering {} with NotificationService", template);
          notificationClient.saveNotificationTemplate(Team.CV, template, true);
        } catch (UnexpectedException ex) {
          log.error(
              "Unable to save {} to NotificationService - skipping register notification templates.", template, ex);
        }
      }
    }
  }

  private void registerSLONotificationIterator(Injector injector) {
    ScheduledThreadPoolExecutor notificationExecutor = new ScheduledThreadPoolExecutor(
        5, new ThreadFactoryBuilder().setNameFormat("slo-notification-iterator").build());
    SLONotificationHandler notificationHandler = injector.getInstance(SLONotificationHandler.class);

    PersistenceIterator dataCollectionIterator =
        MongoPersistenceIterator
            .<AbstractServiceLevelObjective, MorphiaFilterExpander<AbstractServiceLevelObjective>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("SLONotificationIterator")
            .clazz(AbstractServiceLevelObjective.class)
            .fieldName(ServiceLevelObjectiveV2Keys.nextNotificationIteration)
            .targetInterval(ofMinutes(60))
            .acceptableNoAlertDelay(ofMinutes(10))
            .executorService(notificationExecutor)
            .semaphore(new Semaphore(5))
            .handler(notificationHandler)
            .schedulingType(REGULAR)
            .filterExpander(query -> query.criteria(ServiceLevelObjectiveV2Keys.notificationRuleRefs).exists())
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(dataCollectionIterator);
    notificationExecutor.scheduleWithFixedDelay(() -> dataCollectionIterator.process(), 0, 30, TimeUnit.MINUTES);
  }

  private void registerMonitoredServiceNotificationIterator(Injector injector) {
    ScheduledThreadPoolExecutor notificationExecutor = new ScheduledThreadPoolExecutor(
        5, new ThreadFactoryBuilder().setNameFormat("monitoredservice-notification-iterator").build());
    MonitoredServiceNotificationHandler notificationHandler =
        injector.getInstance(MonitoredServiceNotificationHandler.class);

    PersistenceIterator dataCollectionIterator =
        MongoPersistenceIterator.<MonitoredService, MorphiaFilterExpander<MonitoredService>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .iteratorName("MonitoredServiceNotificationIterator")
            .clazz(MonitoredService.class)
            .fieldName(MonitoredServiceKeys.nextNotificationIteration)
            .targetInterval(ofMinutes(5))
            .acceptableNoAlertDelay(ofMinutes(2))
            .executorService(notificationExecutor)
            .semaphore(new Semaphore(5))
            .handler(notificationHandler)
            .schedulingType(REGULAR)
            .filterExpander(query -> query.field(MonitoredServiceKeys.notificationRuleRefs).exists())
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(dataCollectionIterator);
    notificationExecutor.scheduleWithFixedDelay(() -> dataCollectionIterator.process(), 0, 2, TimeUnit.MINUTES);
  }

  private void initializeServiceSecretKeys() {
    // TODO: using env variable directly for now. The whole secret management needs to move to env variable and
    // cv-nextgen should have a new secret with manager along with other services. Change this once everything is
    // standardized for service communication.
    VERIFICATION_SERVICE_SECRET.set(System.getenv(CVNextGenConstants.VERIFICATION_SERVICE_SECRET));
  }

  private void registerManagedBeans(Environment environment, Injector injector) {
    environment.lifecycle().manage(injector.getInstance(MaintenanceController.class));

    // Pipeline SDK Consumers
    environment.lifecycle().manage(injector.getInstance(PipelineEventConsumerController.class));
    environment.lifecycle().manage(injector.getInstance(OutboxEventPollService.class));
  }

  private void registerPmsSdkEvents(Injector injector) {
    log.info("Initializing sdk event redis abstract consumers...");
    PipelineEventConsumerController pipelineEventConsumerController =
        injector.getInstance(PipelineEventConsumerController.class);
    pipelineEventConsumerController.register(injector.getInstance(InterruptEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(OrchestrationEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(FacilitatorEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeStartEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(ProgressEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeAdviseEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeResumeEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(CreatePartialPlanRedisConsumer.class), 1);
  }

  private void registerResources(Environment environment, Injector injector) {
    long startTimeMs = System.currentTimeMillis();
    Set<Class<?>> reflections = getResourceClasses();
    reflections.forEach(resource -> {
      if (!resource.getPackage().getName().endsWith("resources")) {
        throw new IllegalStateException("Resource classes should be in resources package." + resource);
      }
      if (Resource.isAcceptable(resource)) {
        long startTime = System.currentTimeMillis();
        Object resourceClass = injector.getInstance(resource);
        log.info("Time to get instance: " + (System.currentTimeMillis() - startTime) + " ms"
            + resourceClass.getClass().getSimpleName());
        startTime = System.currentTimeMillis();
        environment.jersey().register(resourceClass);
        log.info("Time to register resource: " + (System.currentTimeMillis() - startTime) + " ms"
            + resourceClass.getClass().getSimpleName());
      }
    });
    environment.jersey().register(injector.getInstance(VersionInfoResource.class));
    environment.jersey().register(injector.getInstance(LicenseUsageResource.class));
    environment.jersey().register(injector.getInstance(EnforcementClientResource.class));
    log.info("Registered all the resources. Time taken(ms): {}", System.currentTimeMillis() - startTimeMs);
  }

  private void registerExceptionMappers(JerseyEnvironment jersey) {
    jersey.register(ConstraintViolationExceptionMapper.class);
    jersey.register(NotFoundExceptionMapper.class);
    jersey.register(BadRequestExceptionMapper.class);
    jersey.register(ServiceCallExceptionMapper.class);
    jersey.register(WingsExceptionMapperV2.class);
    jersey.register(GenericExceptionMapperV2.class);
  }

  private void registerCorrelationFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CorrelationFilter.class));
  }

  private void registerTraceFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(TraceFilter.class));
  }

  private void runMigrations(Injector injector) {
    injector.getInstance(CVNGMigrationService.class).runMigrations();
  }

  private void initializeEnforcementSdk(Injector injector) {
    RestrictionUsageRegisterConfiguration restrictionUsageRegisterConfiguration =
        RestrictionUsageRegisterConfiguration.builder()
            .restrictionNameClassMap(
                ImmutableMap.<FeatureRestrictionName, Class<? extends RestrictionUsageInterface>>builder()
                    .put(FeatureRestrictionName.TEST2, ExampleStaticLimitUsageImpl.class)
                    .put(FeatureRestrictionName.TEST3, ExampleRateLimitUsageImpl.class)
                    .put(FeatureRestrictionName.TEST6, ExampleRateLimitUsageImpl.class)
                    .put(FeatureRestrictionName.TEST7, ExampleStaticLimitUsageImpl.class)
                    .put(FeatureRestrictionName.SRM_SERVICES, MaxServiceRestrictionUsageImpl.class)
                    .build())
            .build();
    CustomRestrictionRegisterConfiguration customConfig =
        CustomRestrictionRegisterConfiguration.builder()
            .customRestrictionMap(
                ImmutableMap.<FeatureRestrictionName, Class<? extends CustomRestrictionInterface>>builder()
                    .put(FeatureRestrictionName.TEST4, ExampleCustomImpl.class)
                    .build())
            .build();

    injector.getInstance(EnforcementSdkRegisterService.class)
        .initialize(restrictionUsageRegisterConfiguration, customConfig);
  }

  private Set<Class<?>> getResourceClasses() {
    return HarnessReflections.get()
        .getTypesAnnotatedWith(Path.class)
        .stream()
        .filter(
            klazz -> StringUtils.startsWithAny(klazz.getPackage().getName(), this.getClass().getPackage().getName()))
        .collect(Collectors.toSet());
  }

  private void initAutoscalingMetrics() {
    CVConstants.LEARNING_ENGINE_TASKS_METRIC_LIST.forEach(metricName -> registerGaugeMetric(metricName, null));
  }

  private void registerGaugeMetric(String metricName, String[] labels) {
    harnessMetricRegistry.registerGaugeMetric(metricName, labels, "Metrics from CVNG for LE");
    harnessMetricRegistry.registerGaugeMetric(ENVIRONMENT + "_" + metricName, labels, "Metrics from CVNG for LE");
  }
}
