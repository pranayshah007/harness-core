/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.lock.DistributedLockImplementation.MONGO;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;
import static io.harness.ng.DbAliases.DMS;
import static io.harness.security.ServiceTokenGenerator.VERIFICATION_SERVICE_SECRET;

import static software.wings.beans.alert.Alert.AlertKeys;
import static software.wings.common.VerificationConstants.CV_TASK_CRON_POLL_INTERVAL_SEC;
import static software.wings.common.VerificationConstants.DATA_COLLECTION_TASKS_PER_MINUTE;
import static software.wings.common.VerificationConstants.GA_PER_MINUTE_CV_STATES;
import static software.wings.common.VerificationConstants.IGNORED_ERRORS_METRIC_LABELS;
import static software.wings.common.VerificationConstants.IGNORED_ERRORS_METRIC_NAME;
import static software.wings.common.VerificationConstants.NUM_LOG_RECORDS;
import static software.wings.common.VerificationConstants.getDataAnalysisMetricHelpDocument;

import static com.google.inject.matcher.Matchers.not;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.beans.ExecutionStatus;
import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.controller.PrimaryVersionChangeScheduler;
import io.harness.cvng.core.services.api.VerificationServiceSecretManager;
import io.harness.delegate.authenticator.DelegateSecretManager;
import io.harness.delegate.authenticator.DelegateTokenAuthenticatorImpl;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.exception.violation.ConstraintViolationExceptionMapper;
import io.harness.ff.FeatureFlagConfig;
import io.harness.govern.ProviderModule;
import io.harness.health.HealthService;
import io.harness.iterator.PersistenceIterator;
import io.harness.iterator.PersistenceIterator.ProcessMode;
import io.harness.jobs.sg247.CVConfigurationAnalysisJob;
import io.harness.jobs.sg247.CVConfigurationDataCollectionJob;
import io.harness.jobs.sg247.logs.ServiceGuardCleanUpAlertsJob;
import io.harness.jobs.workflow.WorkflowCVTaskCreationHandler;
import io.harness.jobs.workflow.collection.CVDataCollectionJob;
import io.harness.jobs.workflow.collection.WorkflowDataCollectionJob;
import io.harness.jobs.workflow.logs.WorkflowFeedbackAnalysisJob;
import io.harness.jobs.workflow.logs.WorkflowLogAnalysisJob;
import io.harness.jobs.workflow.logs.WorkflowLogClusterJob;
import io.harness.jobs.workflow.timeseries.WorkflowTimeSeriesAnalysisJob;
import io.harness.lock.DistributedLockImplementation;
import io.harness.maintenance.MaintenanceController;
import io.harness.managerclient.VerificationManagerClientModule;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.metrics.MetricRegistryModule;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.iterator.IteratorConfig;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.mongo.iterator.provider.MorphiaPersistenceRequiredProvider;
import io.harness.morphia.MorphiaModule;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.persistence.HPersistence;
import io.harness.persistence.UserProvider;
import io.harness.persistence.store.Store;
import io.harness.redis.RedisConfig;
import io.harness.resource.VersionInfoResource;
import io.harness.resources.LogVerificationResource;
import io.harness.scheduler.ServiceGuardAccountPoller;
import io.harness.scheduler.WorkflowVerificationTaskPoller;
import io.harness.security.DelegateTokenAuthenticator;
import io.harness.serializer.JsonSubtypeResolver;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.ManagerRegistrars;
import io.harness.serializer.VerificationRegistrars;
import io.harness.service.impl.DelegateSecretManagerImpl;
import io.harness.service.impl.agent.mtls.AgentMtlsEndpointServiceReadOnlyImpl;
import io.harness.service.intfc.AgentMtlsEndpointService;

import software.wings.app.CharsetResponseFilter;
import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.beans.AccountStatus;
import software.wings.beans.AccountType;
import software.wings.beans.LicenseInfo.LicenseInfoKeys;
import software.wings.beans.alert.Alert;
import software.wings.beans.alert.AlertType;
import software.wings.common.VerificationConstants;
import software.wings.dl.WingsPersistence;
import software.wings.exception.GenericExceptionMapper;
import software.wings.exception.JsonProcessingExceptionMapper;
import software.wings.exception.WingsExceptionMapper;
import software.wings.jersey.JsonViews;
import software.wings.security.ThreadLocalUserProvider;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.AnalysisContext.AnalysisContextKeys;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.verification.CVConfiguration;
import software.wings.verification.CVConfiguration.CVConfigurationKeys;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.github.dirkraft.dropwizard.fileassets.FileAssetsBundle;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.name.Named;
import com.palominolabs.metrics.guice.MetricsInstrumentationModule;
import dev.morphia.converters.TypeConverter;
import io.dropwizard.Application;
import io.dropwizard.bundles.assets.ConfiguredAssetsBundle;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import javax.ws.rs.Path;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.model.Resource;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.reflections.Reflections;
import ru.vyarus.guice.validator.ValidationModule;

/**
 * The main application - entry point for the entire verification service.
 */
@Slf4j
public class VerificationServiceApplication extends Application<VerificationServiceConfiguration> {
  // pool interval at which the job will schedule. But here in verificationJob it will schedule at POLL_INTERVAL / 2

  private static String APPLICATION_NAME = "Verification Service Application";
  private final MetricRegistry metricRegistry = new MetricRegistry();
  public HarnessMetricRegistry harnessMetricRegistry;
  private WingsPersistence wingsPersistence;

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
    new VerificationServiceApplication().run(args);
  }

  @Override
  public String getName() {
    return APPLICATION_NAME;
  }

  @Override
  public void initialize(Bootstrap<VerificationServiceConfiguration> bootstrap) {
    initializeLogging();
    log.info("bootstrapping ...");
    bootstrap.addCommand(new InspectCommand<>(this));
    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    bootstrap.addBundle(new ConfiguredAssetsBundle("/static", "/", "index.html"));
    bootstrap.addBundle(new SwaggerBundle<VerificationServiceConfiguration>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(
          VerificationServiceConfiguration verificationServiceConfiguration) {
        return verificationServiceConfiguration.getSwaggerBundleConfiguration();
      }
    });
    bootstrap.addBundle(new FileAssetsBundle("/.well-known"));
    //    bootstrap.getObjectMapper().addMixIn(AssetsConfiguration.class, AssetsConfigurationMixin.class);
    bootstrap.getObjectMapper().setSubtypeResolver(
        new JsonSubtypeResolver(bootstrap.getObjectMapper().getSubtypeResolver()));
    bootstrap.getObjectMapper().setConfig(
        bootstrap.getObjectMapper().getSerializationConfig().withView(JsonViews.Public.class));
    bootstrap.getObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    bootstrap.setMetricRegistry(metricRegistry);

    log.info("bootstrapping done.");
  }

  @Override
  public void run(VerificationServiceConfiguration configuration, Environment environment) {
    log.info("Starting app ...");

    log.info("Entering startup maintenance mode");
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
        return VerificationRegistrars.kryoRegistrars;
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return VerificationRegistrars.morphiaRegistrars;
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(ManagerRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Named("lock")
      @Singleton
      RedisConfig redisConfig() {
        return RedisConfig.builder().build();
      }

      @Provides
      @Singleton
      DistributedLockImplementation distributedLockImplementation() {
        return configuration.getDistributedLockImplementation() == null
            ? MONGO
            : configuration.getDistributedLockImplementation();
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
        return new ThreadLocalUserProvider();
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      @Named("morphiaClasses")
      Map<Class, String> morphiaCustomCollectionNames() {
        return ImmutableMap.<Class, String>builder()
            .put(DelegateSyncTaskResponse.class, "delegateSyncTaskResponses")
            .put(DelegateAsyncTaskResponse.class, "delegateAsyncTaskResponses")
            .put(DelegateTaskProgressResponse.class, "delegateTaskProgressResponses")
            .build();
      }
    });

    modules.add(MorphiaModule.getInstance());
    modules.add(new VerificationServiceModule(configuration));
    modules.add(new VerificationServiceSchedulerModule(configuration));
    modules.add(new VerificationManagerClientModule(configuration.getManagerUrl()));
    modules.add(new MetricRegistryModule(metricRegistry));
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
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        // verification service only needs reading capabilities for datapath authority validation
        bind(AgentMtlsEndpointService.class).to(AgentMtlsEndpointServiceReadOnlyImpl.class);
        bind(DelegateTokenAuthenticator.class).to(DelegateTokenAuthenticatorImpl.class).in(Singleton.class);
        bind(DelegateSecretManager.class).to(DelegateSecretManagerImpl.class);
      }
    });

    Injector injector = Guice.createInjector(modules);

    wingsPersistence = injector.getInstance(WingsPersistence.class);

    harnessMetricRegistry = injector.getInstance(HarnessMetricRegistry.class);

    initMetrics();

    registerStores(configuration, injector);

    registerResources(environment, injector);

    registerManagedBeans(environment, injector);

    initializeServiceSecretKeys(injector);

    registerJerseyProviders(environment);

    registerCharsetResponseFilter(environment, injector);

    // Authentication/Authorization filters
    registerAuthFilters(environment, injector);

    registerHealthChecks(environment, injector);

    registerWorkflowIterators(injector);

    registerServiceGuardIterators(injector, configuration.getServiceGuardIteratorConfig());

    initializeServiceTaskPoll(injector);

    injector.getInstance(PrimaryVersionChangeScheduler.class).registerExecutors();

    log.info("Leaving startup maintenance mode");
    MaintenanceController.resetForceMaintenance();

    log.info("Starting app done");
  }

  private void registerHealthChecks(Environment environment, Injector injector) {
    HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("Verification Service", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  private void initMetrics() {
    VerificationConstants.LEARNING_ENGINE_TASKS_METRIC_LIST.forEach(
        metricName -> registerGaugeMetric(metricName, null));
    registerGaugeMetric(DATA_COLLECTION_TASKS_PER_MINUTE, null);
    registerGaugeMetric(NUM_LOG_RECORDS, null);
    registerGaugeMetric(IGNORED_ERRORS_METRIC_NAME, IGNORED_ERRORS_METRIC_LABELS);
  }

  private void registerGaugeMetric(String metricName, String[] labels) {
    harnessMetricRegistry.registerGaugeMetric(metricName, labels, getDataAnalysisMetricHelpDocument());
    String env = System.getenv("ENV");
    if (isNotEmpty(env)) {
      env = env.replaceAll("-", "_").toLowerCase();
      harnessMetricRegistry.registerGaugeMetric(env + "_" + metricName, labels, getDataAnalysisMetricHelpDocument());
    }
  }

  private void registerResources(Environment environment, Injector injector) {
    Reflections reflections = new Reflections(LogVerificationResource.class.getPackage().getName());

    Set<Class<? extends Object>> resourceClasses = reflections.getTypesAnnotatedWith(Path.class);
    for (Class<?> resource : resourceClasses) {
      if (Resource.isAcceptable(resource)) {
        environment.jersey().register(injector.getInstance(resource));
      }
    }

    environment.jersey().register(injector.getInstance(VersionInfoResource.class));
  }

  private void registerManagedBeans(Environment environment, Injector injector) {
    environment.lifecycle().manage((Managed) injector.getInstance(WingsPersistence.class));
    environment.lifecycle().manage(injector.getInstance(MaintenanceController.class));
  }

  private void registerJerseyProviders(Environment environment) {
    environment.jersey().register(EarlyEofExceptionMapper.class);
    environment.jersey().register(JsonProcessingExceptionMapper.class);
    environment.jersey().register(ConstraintViolationExceptionMapper.class);
    environment.jersey().register(WingsExceptionMapper.class);
    environment.jersey().register(GenericExceptionMapper.class);
    environment.jersey().register(MultiPartFeature.class);
  }

  private void registerAuthFilters(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(VerificationAuthFilter.class));
  }

  private void registerCharsetResponseFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CharsetResponseFilter.class));
  }

  private void registerWorkflowIterators(Injector injector) {
    ScheduledThreadPoolExecutor workflowVerificationExecutor = new ScheduledThreadPoolExecutor(
        10, new ThreadFactoryBuilder().setNameFormat("Iterator-workflow-verification").build());
    registerWorkflowIterator(injector, workflowVerificationExecutor, new WorkflowTimeSeriesAnalysisJob(),
        AnalysisContextKeys.timeSeriesAnalysisIteration, MLAnalysisType.TIME_SERIES, ofMinutes(1), 4);
    registerWorkflowIterator(injector, workflowVerificationExecutor, new WorkflowLogAnalysisJob(),
        AnalysisContextKeys.logAnalysisIteration, MLAnalysisType.LOG_ML, ofSeconds(30), 4);
    registerWorkflowIterator(injector, workflowVerificationExecutor, new WorkflowLogClusterJob(),
        AnalysisContextKeys.logClusterIteration, MLAnalysisType.LOG_ML, ofSeconds(30), 4);
    registerWorkflowIterator(injector, workflowVerificationExecutor, new WorkflowFeedbackAnalysisJob(),
        AnalysisContextKeys.feedbackIteration, MLAnalysisType.LOG_ML, ofSeconds(30), 4);
    registerWorkflowDataCollectionIterator(injector, workflowVerificationExecutor, new WorkflowDataCollectionJob(),
        AnalysisContextKeys.workflowDataCollectionIteration, ofSeconds(60), 4);

    ScheduledThreadPoolExecutor cvTaskWorkflowExecutor = new ScheduledThreadPoolExecutor(
        5, new ThreadFactoryBuilder().setNameFormat("Iterator-cvTask-Workflow-verification").build());
    registerCreateCVTaskIterator(injector, cvTaskWorkflowExecutor, ofSeconds(30), 4);
    registerIterator(injector, cvTaskWorkflowExecutor, new CVDataCollectionJob(),
        AccountKeys.workflowDataCollectionIteration, ofSeconds(CV_TASK_CRON_POLL_INTERVAL_SEC), 4);
  }

  private void registerWorkflowIterator(Injector injector, ScheduledThreadPoolExecutor workflowVerificationExecutor,
      Handler<AnalysisContext> handler, String iteratorFieldName, MLAnalysisType analysisType, Duration interval,
      int maxAllowedThreads) {
    injector.injectMembers(handler);
    PersistenceIterator dataCollectionIterator =
        MongoPersistenceIterator.<AnalysisContext, MorphiaFilterExpander<AnalysisContext>>builder()
            .mode(ProcessMode.PUMP)
            .iteratorName("WorkflowIterator." + iteratorFieldName)
            .clazz(AnalysisContext.class)
            .fieldName(iteratorFieldName)
            .targetInterval(interval)
            .acceptableNoAlertDelay(ofSeconds(30))
            .executorService(workflowVerificationExecutor)
            .semaphore(new Semaphore(maxAllowedThreads))
            .handler(handler)
            .schedulingType(REGULAR)
            .filterExpander(query
                -> query.filter(AnalysisContextKeys.analysisType, analysisType)
                       .field(AnalysisContextKeys.executionStatus)
                       .in(Lists.newArrayList(ExecutionStatus.QUEUED, ExecutionStatus.RUNNING)))
            .redistribute(true)
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .build();
    injector.injectMembers(dataCollectionIterator);
    workflowVerificationExecutor.scheduleAtFixedRate(() -> dataCollectionIterator.process(), 0, 5, TimeUnit.SECONDS);
  }

  private void registerWorkflowDataCollectionIterator(Injector injector,
      ScheduledThreadPoolExecutor workflowVerificationExecutor, Handler<AnalysisContext> handler,
      String iteratorFieldName, Duration interval, int maxAllowedThreads) {
    injector.injectMembers(handler);
    PersistenceIterator dataCollectionIterator =
        MongoPersistenceIterator.<AnalysisContext, MorphiaFilterExpander<AnalysisContext>>builder()
            .mode(ProcessMode.PUMP)
            .iteratorName("WorkflowDataCollectionIterator")
            .clazz(AnalysisContext.class)
            .fieldName(iteratorFieldName)
            .targetInterval(interval)
            .acceptableNoAlertDelay(ofSeconds(30))
            .executorService(workflowVerificationExecutor)
            .semaphore(new Semaphore(maxAllowedThreads))
            .handler(handler)
            .schedulingType(REGULAR)
            .filterExpander(query
                -> query.field(AnalysisContextKeys.stateType)
                       .in(GA_PER_MINUTE_CV_STATES)
                       .field(AnalysisContextKeys.perMinCollectionFinished)
                       .exists()
                       .field(AnalysisContextKeys.perMinCollectionFinished)
                       .equal(false))
            .redistribute(true)
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .build();
    injector.injectMembers(dataCollectionIterator);
    workflowVerificationExecutor.scheduleAtFixedRate(() -> dataCollectionIterator.process(), 0, 5, TimeUnit.SECONDS);
  }

  private void registerAlertsCleanupIterator(Injector injector,
      ScheduledThreadPoolExecutor workflowVerificationExecutor, Duration interval, int maxAllowedThreads) {
    Handler<Alert> handler = new ServiceGuardCleanUpAlertsJob();
    injector.injectMembers(handler);
    PersistenceIterator alertsCleanupIterator =
        MongoPersistenceIterator.<Alert, MorphiaFilterExpander<Alert>>builder()
            .mode(ProcessMode.PUMP)
            .iteratorName("AlertsCleanupIterator")
            .clazz(Alert.class)
            .fieldName(AlertKeys.cvCleanUpIteration)
            .targetInterval(interval)
            .acceptableNoAlertDelay(ofSeconds(30))
            .executorService(workflowVerificationExecutor)
            .semaphore(new Semaphore(maxAllowedThreads))
            .handler(handler)
            .schedulingType(REGULAR)
            .filterExpander(query
                -> query.filter(AlertKeys.type, AlertType.CONTINUOUS_VERIFICATION_ALERT)
                       .field(AlertKeys.createdAt)
                       .lessThanOrEq(Instant.now().minus(6, ChronoUnit.HOURS).toEpochMilli()))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(alertsCleanupIterator);
    workflowVerificationExecutor.scheduleAtFixedRate(() -> alertsCleanupIterator.process(), 0, 10, TimeUnit.MINUTES);
  }

  private void registerCreateCVTaskIterator(Injector injector, ScheduledThreadPoolExecutor workflowVerificationExecutor,
      Duration interval, int maxAllowedThreads) {
    Handler<AnalysisContext> handler = new WorkflowCVTaskCreationHandler();
    injector.injectMembers(handler);
    PersistenceIterator dataCollectionIterator =
        MongoPersistenceIterator.<AnalysisContext, MorphiaFilterExpander<AnalysisContext>>builder()
            .mode(ProcessMode.PUMP)
            .iteratorName("CreateCVTaskIterator")
            .clazz(AnalysisContext.class)
            .fieldName(AnalysisContextKeys.cvTaskCreationIteration)
            .targetInterval(interval)
            .acceptableNoAlertDelay(ofSeconds(30))
            .executorService(workflowVerificationExecutor)
            .semaphore(new Semaphore(maxAllowedThreads))
            .handler(handler)
            .schedulingType(REGULAR)
            .filterExpander(query -> query.filter(AnalysisContextKeys.cvTasksCreated, false))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceRequiredProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(dataCollectionIterator);
    workflowVerificationExecutor.scheduleAtFixedRate(() -> dataCollectionIterator.process(), 0, 5, TimeUnit.SECONDS);
  }

  private void registerServiceGuardIterators(Injector injector, IteratorConfig serviceGuardIteratorConfig) {
    // Increasing thread size to 20 for now. This needs to be redesigned to handle increasing accounts.
    if (serviceGuardIteratorConfig.isEnabled()) {
      ScheduledThreadPoolExecutor serviceGuardExecutor = new ScheduledThreadPoolExecutor(
          20, new ThreadFactoryBuilder().setNameFormat("Iterator-ServiceGuard").build());
      registerServiceGuardIterator(injector, serviceGuardExecutor, new CVConfigurationDataCollectionJob(),
          CVConfigurationKeys.serviceGuardDataCollectionIteration,
          ofSeconds(serviceGuardIteratorConfig.getTargetIntervalInSeconds()),
          serviceGuardIteratorConfig.getThreadPoolCount());
      registerServiceGuardIterator(injector, serviceGuardExecutor, new CVConfigurationAnalysisJob(),
          CVConfigurationKeys.serviceGuardDataAnalysisIteration,
          ofSeconds(serviceGuardIteratorConfig.getTargetIntervalInSeconds()),
          serviceGuardIteratorConfig.getThreadPoolCount());
      registerAlertsCleanupIterator(injector, serviceGuardExecutor,
          ofSeconds(serviceGuardIteratorConfig.getTargetIntervalInSeconds()),
          serviceGuardIteratorConfig.getThreadPoolCount());
    }
  }

  private void registerServiceGuardIterator(Injector injector, ScheduledThreadPoolExecutor serviceGuardExecutor,
      Handler<CVConfiguration> handler, String iteratorFieldName, Duration interval, int maxAllowedThreads) {
    injector.injectMembers(handler);
    MongoPersistenceIterator dataCollectionIterator =
        MongoPersistenceIterator.<CVConfiguration, MorphiaFilterExpander<CVConfiguration>>builder()
            .mode(ProcessMode.PUMP)
            .clazz(CVConfiguration.class)
            .fieldName(iteratorFieldName)
            .targetInterval(interval)
            .acceptableNoAlertDelay(ofSeconds(30))
            .executorService(serviceGuardExecutor)
            .semaphore(new Semaphore(maxAllowedThreads))
            .handler(handler)
            .schedulingType(REGULAR)
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .unsorted(true)
            .build();
    injector.injectMembers(dataCollectionIterator);
    serviceGuardExecutor.scheduleAtFixedRate(() -> dataCollectionIterator.process(), 0, 10, TimeUnit.SECONDS);
  }

  private void registerIterator(Injector injector, ScheduledThreadPoolExecutor serviceGuardExecutor,
      Handler<Account> handler, String iteratorFieldName, Duration interval, int maxAllowedThreads) {
    injector.injectMembers(handler);
    PersistenceIterator dataCollectionIterator =
        MongoPersistenceIterator.<Account, MorphiaFilterExpander<Account>>builder()
            .mode(ProcessMode.PUMP)
            .clazz(Account.class)
            .fieldName(iteratorFieldName)
            .targetInterval(interval)
            .acceptableNoAlertDelay(ofSeconds(30))
            .executorService(serviceGuardExecutor)
            .semaphore(new Semaphore(maxAllowedThreads))
            .handler(handler)
            .schedulingType(REGULAR)
            .filterExpander(query
                -> query.or(query.criteria(AccountKeys.licenseInfo).doesNotExist(),
                    query.and(query.criteria(AccountKeys.licenseInfo + "." + LicenseInfoKeys.accountStatus)
                                  .equal(AccountStatus.ACTIVE)),
                    query.criteria(AccountKeys.licenseInfo + "." + LicenseInfoKeys.accountType)
                        .in(Sets.newHashSet(AccountType.TRIAL, AccountType.PAID))))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(dataCollectionIterator);
    serviceGuardExecutor.scheduleAtFixedRate(() -> dataCollectionIterator.process(), 0, 10, TimeUnit.SECONDS);
  }

  private void initializeServiceSecretKeys(Injector injector) {
    injector.getInstance(VerificationServiceSecretManager.class).initializeServiceSecretKeys();
    VERIFICATION_SERVICE_SECRET.set(
        injector.getInstance(VerificationServiceSecretManager.class).getVerificationServiceSecretKey());
  }

  private void initializeServiceTaskPoll(Injector injector) {
    injector.getInstance(WorkflowVerificationTaskPoller.class).scheduleTaskPoll();
    injector.getInstance(ServiceGuardAccountPoller.class).scheduleAdministrativeTasks();
  }

  private void registerStores(VerificationServiceConfiguration configuration, Injector injector) {
    final HPersistence persistence = injector.getInstance(HPersistence.class);
    if (isNotEmpty(configuration.getDmsMongo().getUri())
        && !configuration.getDmsMongo().getUri().equals(configuration.getMongoConnectionFactory().getUri())) {
      persistence.register(Store.builder().name(DMS).build(), configuration.getDmsMongo().getUri());
    }
  }
}
