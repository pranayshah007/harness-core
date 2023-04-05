/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.app.CIManagerConfiguration.HARNESS_RESOURCE_CLASSES;
import static io.harness.configuration.DeployVariant.DEPLOY_VERSION;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.EventsFrameworkConstants.OBSERVER_EVENT_CHANNEL;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.pms.contracts.plan.ExpansionRequestType.KEY;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;

import static java.util.Collections.singletonList;

import io.harness.Microservice;
import io.harness.ModuleType;
import io.harness.PipelineServiceUtilityModule;
import io.harness.SCMGrpcClientModule;
import io.harness.accesscontrol.NGAccessDeniedExceptionMapper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.migration.CIManagerMigrationProvider;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.cache.CacheModule;
import io.harness.ci.app.InspectCommand;
import io.harness.ci.enforcement.BuildRestrictionUsageImpl;
import io.harness.ci.enforcement.BuildsPerDayRestrictionUsageImpl;
import io.harness.ci.enforcement.BuildsPerMonthRestrictionUsageImpl;
import io.harness.ci.enforcement.TotalBuildsRestrictionUsageImpl;
import io.harness.ci.execution.ObserverEventConsumer;
import io.harness.ci.execution.OrchestrationExecutionEventHandlerRegistrar;
import io.harness.ci.execution.queue.CIExecutionPoller;
import io.harness.ci.plan.creator.CIModuleInfoProvider;
import io.harness.ci.plan.creator.CIPipelineServiceInfoProvider;
import io.harness.ci.plan.creator.filter.CIFilterCreationResponseMerger;
import io.harness.ci.registrars.ExecutionAdvisers;
import io.harness.ci.registrars.ExecutionRegistrar;
import io.harness.ci.serializer.CiExecutionRegistrars;
import io.harness.controller.PrimaryVersionChangeScheduler;
import io.harness.core.ci.services.CIActiveCommitterUsageImpl;
import io.harness.core.ci.services.CICacheAllowanceImpl;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.enforcement.client.CustomRestrictionRegisterConfiguration;
import io.harness.enforcement.client.RestrictionUsageRegisterConfiguration;
import io.harness.enforcement.client.services.EnforcementSdkRegisterService;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.exception.GeneralException;
import io.harness.govern.ProviderModule;
import io.harness.governance.DefaultConnectorRefExpansionHandler;
import io.harness.health.HealthService;
import io.harness.maintenance.MaintenanceController;
import io.harness.migration.MigrationProvider;
import io.harness.migration.NGMigrationSdkInitHelper;
import io.harness.migration.NGMigrationSdkModule;
import io.harness.migration.beans.NGMigrationConfiguration;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.CorrelationFilter;
import io.harness.ng.core.TraceFilter;
import io.harness.ng.core.exceptionmappers.GenericExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.JerseyViolationExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.NotAllowedExceptionMapper;
import io.harness.ng.core.exceptionmappers.NotFoundExceptionMapper;
import io.harness.ng.core.exceptionmappers.WingsExceptionMapperV2;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.persistence.store.Store;
import io.harness.plugin.PluginMetadataRecordsJob;
import io.harness.pms.contracts.plan.JsonExpansionInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.events.base.PipelineEventConsumerController;
import io.harness.pms.listener.NgOrchestrationNotifyEventListener;
import io.harness.pms.sdk.PmsSdkConfiguration;
import io.harness.pms.sdk.PmsSdkInitHelper;
import io.harness.pms.sdk.PmsSdkModule;
import io.harness.pms.sdk.core.SdkDeployMode;
import io.harness.pms.sdk.core.governance.JsonExpansionHandlerInfo;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.pms.sdk.execution.events.facilitators.FacilitatorEventRedisConsumer;
import io.harness.pms.sdk.execution.events.interrupts.InterruptEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.advise.NodeAdviseEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.resume.NodeResumeEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.start.NodeStartEventRedisConsumer;
import io.harness.pms.sdk.execution.events.orchestrationevent.OrchestrationEventRedisConsumer;
import io.harness.pms.sdk.execution.events.plan.CreatePartialPlanRedisConsumer;
import io.harness.pms.sdk.execution.events.progress.ProgressEventRedisConsumer;
import io.harness.pms.serializer.json.PmsBeansJacksonModule;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.queue.QueueListenerController;
import io.harness.queue.QueuePublisher;
import io.harness.resource.VersionInfoResource;
import io.harness.security.NextGenAuthenticationFilter;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.serializer.CiBeansRegistrars;
import io.harness.serializer.ConnectorNextGenRegistrars;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.PersistenceRegistrars;
import io.harness.serializer.PrimaryVersionManagerRegistrars;
import io.harness.serializer.YamlBeansModuleRegistrars;
import io.harness.service.impl.DelegateAsyncServiceImpl;
import io.harness.service.impl.DelegateProgressServiceImpl;
import io.harness.service.impl.DelegateSyncServiceImpl;
import io.harness.telemetry.CiTelemetryRecordsJob;
import io.harness.token.remote.TokenClient;
import io.harness.waiter.NotifierScheduledExecutorService;
import io.harness.waiter.NotifyEvent;
import io.harness.waiter.NotifyQueuePublisherRegister;
import io.harness.waiter.NotifyResponseCleaner;
import io.harness.waiter.ProgressUpdateService;
import io.harness.yaml.YamlSdkConfiguration;
import io.harness.yaml.YamlSdkInitHelper;
import io.harness.yaml.YamlSdkModule;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.LogManager;
import org.glassfish.jersey.server.model.Resource;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.springframework.core.convert.converter.Converter;
import ru.vyarus.guice.validator.ValidationModule;

@Slf4j
@OwnedBy(CI)
public class CIManagerApplication extends Application<CIManagerConfiguration> {
  private static final SecureRandom random = new SecureRandom();
  public static final Store HARNESS_STORE = Store.builder().name("harness").build();
  private static final String APP_NAME = "CI Manager Service Application";

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));
    new CIManagerApplication().run(args);
  }

  @Override
  public String getName() {
    return APP_NAME;
  }

  public static void configureObjectMapper(final ObjectMapper mapper) {
    HObjectMapper.configureObjectMapperForNG(mapper);
    mapper.registerModule(new PmsBeansJacksonModule());
  }

  @Override
  public void run(CIManagerConfiguration configuration, Environment environment) {
    log.info("Starting ci manager app ...");

    log.info("Entering startup maintenance mode");
    MaintenanceController.forceMaintenance(true);

    log.info("Leaving startup maintenance mode");
    List<Module> modules = new ArrayList<>();
    modules.add(KryoModule.getInstance());
    modules.add(NGMigrationSdkModule.getInstance());
    modules.add(new SCMGrpcClientModule(configuration.getScmConnectionConfig()));
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> registrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(YamlBeansModuleRegistrars.kryoRegistrars)
            .addAll(CiBeansRegistrars.kryoRegistrars)
            .addAll(CiExecutionRegistrars.kryoRegistrars)
            .addAll(ConnectorNextGenRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(CiExecutionRegistrars.morphiaRegistrars)
            .addAll(PrimaryVersionManagerRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      @Named("morphiaClasses")
      Map<Class, String> morphiaCustomCollectionNames() {
        return ImmutableMap.<Class, String>builder()
            .put(DelegateSyncTaskResponse.class, "ciManager_delegateSyncTaskResponses")
            .put(DelegateAsyncTaskResponse.class, "ciManager_delegateAsyncTaskResponses")
            .put(DelegateTaskProgressResponse.class, "ciManager_delegateTaskProgressResponses")
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(PersistenceRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder().build();
      }
      @Provides
      @Singleton
      List<YamlSchemaRootClass> yamlSchemaRootClasses() {
        return ImmutableList.<YamlSchemaRootClass>builder().addAll(CiBeansRegistrars.yamlSchemaRegistrars).build();
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return configuration.getHarnessCIMongo();
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
        return new NoopUserProvider();
      }
    });

    modules.add(new CIPersistenceModule());
    addGuiceValidationModule(modules);
    modules.add(new CIManagerServiceModule(configuration, new CIManagerConfigurationOverride()));
    modules.add(new CacheModule(configuration.getCacheConfig()));

    modules.add(YamlSdkModule.getInstance());

    PmsSdkConfiguration ciPmsSdkConfiguration = getPmsSdkConfiguration(
        configuration, ModuleType.CI, ExecutionRegistrar.getEngineSteps(), CIPipelineServiceInfoProvider.class);
    modules.add(PmsSdkModule.getInstance(ciPmsSdkConfiguration));

    modules.add(PipelineServiceUtilityModule.getInstance());

    Injector injector = Guice.createInjector(modules);
    registerPMSSDK(configuration, injector);
    registerMigrations(injector);
    registerResources(environment, injector);
    registerWaitEnginePublishers(injector);
    registerManagedBeans(environment, injector, configuration);
    registerHealthCheck(environment, injector);
    registerAuthFilters(configuration, environment, injector);
    registerExceptionMappers(environment);
    registerCorrelationFilter(environment, injector);
    registerStores(configuration, injector);
    registerYamlSdk(injector);
    scheduleJobs(injector, configuration);
    registerQueueListener(injector);
    registerPmsSdkEvents(injector);
    initializeEnforcementFramework(injector);
    registerEventConsumers(injector);

    if (BooleanUtils.isTrue(configuration.getEnableOpentelemetry())) {
      registerTraceFilter(environment, injector);
    }

    log.info("CIManagerApplication DEPLOY_VERSION = " + System.getenv().get(DEPLOY_VERSION));
    initializeCiManagerMonitoring(configuration, injector);

    initializePluginPublisher(injector);
    registerOasResource(configuration, environment, injector);
    log.info("Starting app done");
    MaintenanceController.forceMaintenance(false);
    LogManager.shutdown();
  }

  private void registerEventConsumers(final Injector injector) {
    final ExecutorService entityCRUDConsumerExecutor =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(OBSERVER_EVENT_CHANNEL).build());
    entityCRUDConsumerExecutor.execute(injector.getInstance(ObserverEventConsumer.class));
  }

  private void registerOasResource(CIManagerConfiguration appConfig, Environment environment, Injector injector) {
    OpenApiResource openApiResource = injector.getInstance(OpenApiResource.class);
    openApiResource.setOpenApiConfiguration(appConfig.getOasConfig());
    environment.jersey().register(openApiResource);
  }

  private void registerQueueListener(Injector injector) {
    log.info("Initializing queue listeners...");
    QueueListenerController queueListenerController = injector.getInstance(QueueListenerController.class);
    queueListenerController.register(injector.getInstance(NgOrchestrationNotifyEventListener.class), 1);
  }

  @Override
  public void initialize(Bootstrap<CIManagerConfiguration> bootstrap) {
    initializeLogging();
    log.info("bootstrapping ...");
    bootstrap.addCommand(new InspectCommand<>(this));
    bootstrap.addCommand(new ScanClasspathMetadataCommand());
    bootstrap.addCommand(new GenerateOpenApiSpecCommand());

    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));

    configureObjectMapper(bootstrap.getObjectMapper());
    bootstrap.addBundle(new SwaggerBundle<CIManagerConfiguration>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(CIManagerConfiguration appConfig) {
        return appConfig.getSwaggerBundleConfiguration();
      }
    });
    log.info("bootstrapping done.");
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : HARNESS_RESOURCE_CLASSES) {
      if (Resource.isAcceptable(resource)) {
        environment.jersey().register(injector.getInstance(resource));
      }
    }
    environment.jersey().register(injector.getInstance(VersionInfoResource.class));
  }

  private void registerPMSSDK(CIManagerConfiguration config, Injector injector) {
    PmsSdkConfiguration ciSDKConfig = getPmsSdkConfiguration(
        config, ModuleType.CI, ExecutionRegistrar.getEngineSteps(), CIPipelineServiceInfoProvider.class);
    if (ciSDKConfig.getDeploymentMode().equals(SdkDeployMode.REMOTE)) {
      try {
        PmsSdkInitHelper.initializeSDKInstance(injector, ciSDKConfig);
      } catch (Exception e) {
        throw new GeneralException("Fail to start ci manager because pms sdk registration failed", e);
      }
    }
  }

  private PmsSdkConfiguration getPmsSdkConfiguration(CIManagerConfiguration config, ModuleType moduleType,
      Map<StepType, Class<? extends Step>> engineSteps,
      Class<? extends io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoProvider>
          pipelineServiceInfoProviderClass) {
    boolean remote = false;
    if (config.getShouldConfigureWithPMS() != null && config.getShouldConfigureWithPMS()) {
      remote = true;
    }

    return PmsSdkConfiguration.builder()
        .deploymentMode(remote ? SdkDeployMode.REMOTE : SdkDeployMode.LOCAL)
        .moduleType(moduleType)
        .pipelineServiceInfoProviderClass(pipelineServiceInfoProviderClass)
        .grpcServerConfig(config.getPmsSdkGrpcServerConfig())
        .pmsGrpcClientConfig(config.getPmsGrpcClientConfig())
        .filterCreationResponseMerger(new CIFilterCreationResponseMerger())
        .engineSteps(engineSteps)
        .executionSummaryModuleInfoProviderClass(CIModuleInfoProvider.class)
        .engineAdvisers(ExecutionAdvisers.getEngineAdvisers())
        .engineEventHandlersMap(OrchestrationExecutionEventHandlerRegistrar.getEngineEventHandlers())
        .eventsFrameworkConfiguration(config.getEventsFrameworkConfiguration())
        .executionPoolConfig(config.getPmsSdkExecutionPoolConfig())
        .orchestrationEventPoolConfig(config.getPmsSdkOrchestrationEventPoolConfig())
        .planCreatorServiceInternalConfig(config.getPmsPlanCreatorServicePoolConfig())
        .jsonExpansionHandlers(getJsonExpansionHandlers())
        .build();
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

  private void scheduleJobs(Injector injector, CIManagerConfiguration config) {
    injector.getInstance(PrimaryVersionChangeScheduler.class).registerExecutors();
    injector.getInstance(NotifierScheduledExecutorService.class)
        .scheduleWithFixedDelay(
            injector.getInstance(NotifyResponseCleaner.class), random.nextInt(300), 300L, TimeUnit.SECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateSyncServiceImpl.class), 0L, 2L, TimeUnit.SECONDS);

    for (int i = 0; i < config.getAsyncDelegateResponseConsumption().getCorePoolSize(); i++) {
      injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("async-taskPollExecutor")))
          .scheduleWithFixedDelay(injector.getInstance(DelegateAsyncServiceImpl.class), 0L, 5L, TimeUnit.SECONDS);
    }
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateProgressServiceImpl.class), 0L, 5L, TimeUnit.SECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(ProgressUpdateService.class), 0L, 5L, TimeUnit.SECONDS);
  }

  private void registerManagedBeans(Environment environment, Injector injector, CIManagerConfiguration config) {
    environment.lifecycle().manage(injector.getInstance(QueueListenerController.class));
    environment.lifecycle().manage(injector.getInstance(NotifierScheduledExecutorService.class));
    environment.lifecycle().manage(injector.getInstance(PipelineEventConsumerController.class));
    boolean local = config.getCiExecutionServiceConfig().isLocal();
    if (!local) {
      environment.lifecycle().manage(injector.getInstance(CIExecutionPoller.class));
    }
    // Do not remove as it's used for MaintenanceController for shutdown mode
    environment.lifecycle().manage(injector.getInstance(MaintenanceController.class));
  }

  private void registerPmsSdkEvents(Injector injector) {
    log.info("Initializing redis abstract consumers...");
    PipelineEventConsumerController pipelineEventConsumerController =
        injector.getInstance(PipelineEventConsumerController.class);
    pipelineEventConsumerController.register(injector.getInstance(InterruptEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(OrchestrationEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(FacilitatorEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeStartEventRedisConsumer.class), 2);
    pipelineEventConsumerController.register(injector.getInstance(ProgressEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeAdviseEventRedisConsumer.class), 2);
    pipelineEventConsumerController.register(injector.getInstance(NodeResumeEventRedisConsumer.class), 2);
    pipelineEventConsumerController.register(injector.getInstance(CreatePartialPlanRedisConsumer.class), 2);
  }

  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("CI Service", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  private static void addGuiceValidationModule(List<Module> modules) {
    ValidatorFactory validatorFactory = Validation.byDefaultProvider()
                                            .configure()
                                            .parameterNameProvider(new ReflectionParameterNameProvider())
                                            .buildValidatorFactory();
    modules.add(new ValidationModule(validatorFactory));
  }

  private static void registerStores(CIManagerConfiguration config, Injector injector) {
    final String ciMongo = config.getHarnessCIMongo().getUri();
    if (isNotEmpty(ciMongo) && !ciMongo.equals(config.getHarnessMongo().getUri())) {
      final HPersistence hPersistence = injector.getInstance(HPersistence.class);
      hPersistence.register(HARNESS_STORE, config.getHarnessMongo().getUri());
    }
  }

  private void registerWaitEnginePublishers(Injector injector) {
    final QueuePublisher<NotifyEvent> publisher =
        injector.getInstance(Key.get(new TypeLiteral<QueuePublisher<NotifyEvent>>() {}));
    final NotifyQueuePublisherRegister notifyQueuePublisherRegister =
        injector.getInstance(NotifyQueuePublisherRegister.class);
    notifyQueuePublisherRegister.register(
        NG_ORCHESTRATION, payload -> publisher.send(singletonList(NG_ORCHESTRATION), payload));
  }

  private void registerAuthFilters(CIManagerConfiguration configuration, Environment environment, Injector injector) {
    if (configuration.isEnableAuth()) {
      Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate = resourceInfoAndRequest
          -> resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(NextGenManagerAuth.class) != null
          || resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(NextGenManagerAuth.class) != null;
      Map<String, String> serviceToSecretMapping = new HashMap<>();
      serviceToSecretMapping.put(AuthorizationServiceHeader.BEARER.getServiceId(), configuration.getJwtAuthSecret());
      serviceToSecretMapping.put(
          AuthorizationServiceHeader.IDENTITY_SERVICE.getServiceId(), configuration.getJwtIdentityServiceSecret());
      serviceToSecretMapping.put(
          AuthorizationServiceHeader.DEFAULT.getServiceId(), configuration.getNgManagerServiceSecret());
      environment.jersey().register(new NextGenAuthenticationFilter(predicate, null, serviceToSecretMapping,
          injector.getInstance(Key.get(TokenClient.class, Names.named("PRIVILEGED")))));
    }
  }
  private void registerExceptionMappers(Environment environment) {
    environment.jersey().register(JerseyViolationExceptionMapperV2.class);
    environment.jersey().register(GenericExceptionMapperV2.class);
    environment.jersey().register(new JsonProcessingExceptionMapper(true));
    environment.jersey().register(EarlyEofExceptionMapper.class);
    environment.jersey().register(NGAccessDeniedExceptionMapper.class);
    environment.jersey().register(WingsExceptionMapperV2.class);
    environment.jersey().register(NotFoundExceptionMapper.class);
    environment.jersey().register(NotAllowedExceptionMapper.class);
  }

  private void registerCorrelationFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CorrelationFilter.class));
  }

  private void registerTraceFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(TraceFilter.class));
  }

  private void registerYamlSdk(Injector injector) {
    YamlSdkConfiguration yamlSdkConfiguration = YamlSdkConfiguration.builder()
                                                    .requireSchemaInit(true)
                                                    .requireSnippetInit(true)
                                                    .requireValidatorInit(false)
                                                    .build();
    YamlSdkInitHelper.initialize(injector, yamlSdkConfiguration);
  }

  private void registerMigrations(Injector injector) {
    NGMigrationConfiguration config = getMigrationSdkConfiguration();
    NGMigrationSdkInitHelper.initialize(injector, config);
  }

  private NGMigrationConfiguration getMigrationSdkConfiguration() {
    return NGMigrationConfiguration.builder()
        .microservice(Microservice.CI)
        .migrationProviderList(new ArrayList<Class<? extends MigrationProvider>>() {
          { add(CIManagerMigrationProvider.class); }
        })
        .build();
  }

  private void initializeEnforcementFramework(Injector injector) {
    CustomRestrictionRegisterConfiguration customConfig =
        CustomRestrictionRegisterConfiguration.builder()
            .customRestrictionMap(
                ImmutableMap
                    .<FeatureRestrictionName,
                        Class<? extends io.harness.enforcement.client.custom.CustomRestrictionInterface>>builder()
                    .put(FeatureRestrictionName.BUILDS, BuildRestrictionUsageImpl.class)
                    .build())
            .build();
    RestrictionUsageRegisterConfiguration restrictionUsageRegisterConfiguration =
        RestrictionUsageRegisterConfiguration.builder()
            .restrictionNameClassMap(
                ImmutableMap.<FeatureRestrictionName, Class<? extends RestrictionUsageInterface>>builder()
                    .put(FeatureRestrictionName.ACTIVE_COMMITTERS, CIActiveCommitterUsageImpl.class)
                    .put(FeatureRestrictionName.MAX_TOTAL_BUILDS, TotalBuildsRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MAX_BUILDS_PER_MONTH, BuildsPerMonthRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MAX_BUILDS_PER_DAY, BuildsPerDayRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.CACHE_SIZE_ALLOWANCE, CICacheAllowanceImpl.class)
                    .build())
            .build();
    injector.getInstance(EnforcementSdkRegisterService.class)
        .initialize(restrictionUsageRegisterConfiguration, customConfig);
  }

  private void initializeCiManagerMonitoring(CIManagerConfiguration config, Injector injector) {
    if (BooleanUtils.isTrue(config.getEnableTelemetry())) {
      log.info("Initializing CI Manager Monitoring");
      injector.getInstance(CiTelemetryRecordsJob.class).scheduleTasks();
    }
  }

  private void initializePluginPublisher(Injector injector) {
    log.info("Initializing plugin metadata publishing job");
    injector.getInstance(PluginMetadataRecordsJob.class).scheduleTasks();
  }
}
