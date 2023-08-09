/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import static io.harness.annotations.dev.HarnessTeam.STO;
import static io.harness.app.STOManagerConfiguration.BASE_PACKAGE;
import static io.harness.app.STOManagerConfiguration.NG_PIPELINE_PACKAGE;
import static io.harness.authorization.AuthorizationServiceHeader.STO_MANAGER;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.EventsFrameworkConstants.OBSERVER_EVENT_CHANNEL;
import static io.harness.eventsframework.EventsFrameworkConstants.STO_ORCHESTRATION_NOTIFY_EVENT;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.pms.contracts.plan.ExpansionRequestType.KEY;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;

import static java.util.Collections.singletonList;

import io.harness.ModuleType;
import io.harness.PipelineServiceUtilityModule;
import io.harness.SCMGrpcClientModule;
import io.harness.accesscontrol.NGAccessDeniedExceptionMapper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.telemetry.STOTelemetryRecordsJob;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.cache.CacheModule;
import io.harness.ci.execution.ObserverEventConsumer;
import io.harness.ci.execution.OrchestrationExecutionEventHandlerRegistrar;
import io.harness.ci.execution.queue.CIExecutionPoller;
import io.harness.ci.plan.creator.CIModuleInfoProvider;
import io.harness.ci.plan.creator.filter.CIFilterCreationResponseMerger;
import io.harness.ci.registrars.ExecutionAdvisers;
import io.harness.ci.serializer.CiExecutionRegistrars;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.exception.GeneralException;
import io.harness.govern.ProviderModule;
import io.harness.governance.DefaultConnectorRefExpansionHandler;
import io.harness.health.HealthService;
import io.harness.license.STOLicenseNoopServiceImpl;
import io.harness.maintenance.MaintenanceController;
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
import io.harness.pms.listener.NgOrchestrationNotifyEventListenerNonVersioned;
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
import io.harness.queue.QueueController;
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
import io.harness.serializer.StoBeansRegistrars;
import io.harness.serializer.YamlBeansModuleRegistrars;
import io.harness.service.impl.DelegateAsyncServiceImpl;
import io.harness.service.impl.DelegateProgressServiceImpl;
import io.harness.service.impl.DelegateSyncServiceImpl;
import io.harness.sto.execution.STONotifyEventConsumerRedis;
import io.harness.sto.execution.STONotifyEventPublisher;
import io.harness.sto.plan.creator.STOPipelineServiceInfoProvider;
import io.harness.sto.registrars.STOExecutionRegistrar;
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
import com.google.inject.AbstractModule;
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
import java.util.Collection;
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
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.LogManager;
import org.glassfish.jersey.server.model.Resource;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.reflections.Reflections;
import org.springframework.core.convert.converter.Converter;
import ru.vyarus.guice.validator.ValidationModule;

@Slf4j
@OwnedBy(STO)
public class STOManagerApplication extends Application<CIManagerConfiguration> {
  private static final SecureRandom random = new SecureRandom();
  public static final Store HARNESS_STORE = Store.builder().name("harness").build();
  private static final String APP_NAME = "STO Manager Service Application";
  private static final String STO_ORCHESTRATION = "sto_orchestration";

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));
    new STOManagerApplication().run(args);
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
    log.info("Starting sto manager app ...");

    log.info("Entering startup maintenance mode");
    MaintenanceController.forceMaintenance(true);

    log.info("Leaving startup maintenance mode");
    List<Module> modules = new ArrayList<>();
    modules.add(KryoModule.getInstance());
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
            .put(DelegateSyncTaskResponse.class, "stoManager_delegateSyncTaskResponses")
            .put(DelegateAsyncTaskResponse.class, "stoManager_delegateAsyncTaskResponses")
            .put(DelegateTaskProgressResponse.class, "stoManager_delegateTaskProgressResponses")
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
        return ImmutableList.<YamlSchemaRootClass>builder().addAll(StoBeansRegistrars.yamlSchemaRegistrars).build();
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return STOManagerConfiguration.getHarnessSTOMongo(configuration.getHarnessCIMongo());
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
    String mongoUri = STOManagerConfiguration.getHarnessSTOMongo(configuration.getHarnessCIMongo()).getUri();
    modules.add(new CIManagerServiceModule(configuration,
        new CIManagerConfigurationOverride(STO_MANAGER, "sto", false, false, mongoUri, STO_ORCHESTRATION_NOTIFY_EVENT,
            STOLicenseNoopServiceImpl.class)));
    modules.add(new STOManagerServiceModule());

    modules.add(new AbstractModule() {
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

    modules.add(new CacheModule(configuration.getCacheConfig()));

    modules.add(YamlSdkModule.getInstance());

    // Pipeline Service Modules
    PmsSdkConfiguration stoPmsSdkConfiguration = getPmsSdkConfiguration(
        configuration, ModuleType.STO, STOExecutionRegistrar.getEngineSteps(), STOPipelineServiceInfoProvider.class);
    modules.add(PmsSdkModule.getInstance(stoPmsSdkConfiguration));

    modules.add(PipelineServiceUtilityModule.getInstance());

    Injector injector = Guice.createInjector(modules);
    registerPMSSDK(configuration, injector);
    registerResources(environment, injector);
    registerWaitEnginePublishers(injector);
    registerManagedBeans(environment, injector, configuration);
    registerHealthCheck(environment, injector);
    registerAuthFilters(configuration, environment, injector);
    registerCorrelationFilter(environment, injector);
    registerStores(configuration, injector);
    registerYamlSdk(injector);
    scheduleJobs(injector, configuration);
    registerQueueListener(injector);
    registerPmsSdkEvents(injector);
    registerExceptionMappers(environment);

    if (BooleanUtils.isTrue(configuration.getEnableOpentelemetry())) {
      registerTraceFilter(environment, injector);
    }

    initializeSTOUsageMonitoring(configuration, injector);

    initializePluginPublisher(injector);
    registerEventConsumers(injector);
    registerOasResource(configuration, environment, injector);
    log.info("Starting app done");
    MaintenanceController.forceMaintenance(false);
    LogManager.shutdown();
  }

  public static Collection<Class<?>> getResourceClasses() {
    Reflections basePackageClasses = new Reflections(BASE_PACKAGE);
    Set<Class<?>> classSet = basePackageClasses.getTypesAnnotatedWith(Path.class);
    Reflections pipelinePackageClasses = new Reflections(NG_PIPELINE_PACKAGE);
    classSet.addAll(pipelinePackageClasses.getTypesAnnotatedWith(Path.class));

    return classSet;
  }

  private void registerEventConsumers(final Injector injector) {
    final ExecutorService observerEventConsumerExecutor =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(OBSERVER_EVENT_CHANNEL).build());
    observerEventConsumerExecutor.execute(injector.getInstance(ObserverEventConsumer.class));
  }

  private void registerOasResource(CIManagerConfiguration appConfig, Environment environment, Injector injector) {
    OpenApiResource openApiResource = injector.getInstance(OpenApiResource.class);
    openApiResource.setOpenApiConfiguration(
        STOManagerConfiguration.getOasConfig(appConfig.getHostname(), appConfig.getBasePathPrefix()));
    environment.jersey().register(openApiResource);
  }

  private void registerQueueListener(Injector injector) {
    log.info("Initializing queue listeners...");
    QueueListenerController queueListenerController = injector.getInstance(QueueListenerController.class);
    queueListenerController.register(injector.getInstance(NgOrchestrationNotifyEventListenerNonVersioned.class), 1);
  }

  @Override
  public void initialize(Bootstrap<CIManagerConfiguration> bootstrap) {
    initializeLogging();
    log.info("bootstrapping ...");
    bootstrap.addCommand(new GenerateOpenApiSpecCommand());

    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));

    configureObjectMapper(bootstrap.getObjectMapper());
    bootstrap.addBundle(new SwaggerBundle<CIManagerConfiguration>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(CIManagerConfiguration appConfig) {
        return STOManagerConfiguration.getSwaggerBundleConfiguration(appConfig.getSwaggerBundleConfiguration());
      }
    });
    log.info("bootstrapping done.");
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : getResourceClasses()) {
      if (Resource.isAcceptable(resource)) {
        environment.jersey().register(injector.getInstance(resource));
      }
    }
    environment.jersey().register(injector.getInstance(VersionInfoResource.class));
  }

  private void registerPMSSDK(CIManagerConfiguration config, Injector injector) {
    PmsSdkConfiguration stoSDKConfig = getPmsSdkConfiguration(
        config, ModuleType.STO, STOExecutionRegistrar.getEngineSteps(), STOPipelineServiceInfoProvider.class);
    if (stoSDKConfig.getDeploymentMode().equals(SdkDeployMode.REMOTE)) {
      try {
        PmsSdkInitHelper.initializeSDKInstance(injector, stoSDKConfig);
      } catch (Exception e) {
        throw new GeneralException("Fail to start STO manager because pms sdk registration failed", e);
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

    if (config.getEnableQueue()) {
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
    pipelineEventConsumerController.register(injector.getInstance(STONotifyEventConsumerRedis.class), 15);
  }

  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("STO Service", healthService);
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
    final String stoMongo = STOManagerConfiguration.getHarnessSTOMongo(config.getHarnessCIMongo()).getUri();
    if (isNotEmpty(stoMongo) && !stoMongo.equals(config.getHarnessMongo().getUri())) {
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
    notifyQueuePublisherRegister.register(STO_ORCHESTRATION, injector.getInstance(STONotifyEventPublisher.class));
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

  private void initializePluginPublisher(Injector injector) {
    log.info("Initializing plugin metadata publishing job");
    injector.getInstance(PluginMetadataRecordsJob.class).scheduleTasks();
  }

  private void initializeSTOUsageMonitoring(CIManagerConfiguration config, Injector injector) {
    if (BooleanUtils.isTrue(config.getEnableTelemetry())) {
      log.info("Initializing STO Manager Monitoring");
      injector.getInstance(STOTelemetryRecordsJob.class).scheduleTasks();
    }
  }
}
