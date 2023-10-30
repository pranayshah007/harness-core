/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration;
import static io.harness.NGConstants.X_API_KEY;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.GLOBAL_DISABLE_HEALTH_CHECK;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.lock.mongo.MongoPersistentLocker.LOCKS_STORE;
import static io.harness.logging.LoggingInitializer.initializeLogging;

import static com.google.common.collect.ImmutableMap.of;
import static com.google.inject.matcher.Matchers.not;
import static com.google.inject.name.Names.named;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.app.GraphQLModule;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.cache.CacheModule;
import io.harness.ccm.CEPerpetualTaskHandler;
import io.harness.ccm.KubernetesClusterHandler;
import io.harness.ccm.cluster.ClusterRecordHandler;
import io.harness.ccm.cluster.ClusterRecordObserver;
import io.harness.ccm.cluster.ClusterRecordServiceImpl;
import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.commandlibrary.client.CommandLibraryServiceClientModule;
import io.harness.config.DatadogConfig;
import io.harness.config.PublisherConfiguration;
import io.harness.config.WorkersConfiguration;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.delegate.beans.StartupMode;
import io.harness.delegate.event.handler.DelegateProfileEventHandler;
import io.harness.event.EventsModule;
import io.harness.event.usagemetrics.EventsModuleHelper;
import io.harness.eventframework.dms.DmsObserverEventProducer;
import io.harness.eventframework.manager.ManagerObserverEventProducer;
import io.harness.exception.violation.ConstraintViolationExceptionMapper;
import io.harness.ff.FeatureFlagConfig;
import io.harness.ff.FeatureFlagService;
import io.harness.filter.FiltersModule;
import io.harness.govern.ProviderModule;
import io.harness.grpc.GrpcServiceConfigurationModule;
import io.harness.grpc.server.GrpcServerConfig;
import io.harness.health.HealthMonitor;
import io.harness.health.HealthService;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLocker;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.metrics.MetricRegistryModule;
import io.harness.migrations.MigrationModule;
import io.harness.module.DelegateServiceModule;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.tracing.TraceMode;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.CorrelationFilter;
import io.harness.ng.core.TraceFilter;
import io.harness.ngmigration.api.NgMigrationResource;
import io.harness.ngmigration.serializer.CurrentGenRegistrars;
import io.harness.observer.NoOpRemoteObserverInformerImpl;
import io.harness.observer.RemoteObserver;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.consumer.AbstractRemoteObserverModule;
import io.harness.perpetualtask.PerpetualTaskServiceImpl;
import io.harness.perpetualtask.internal.PerpetualTaskRecordHandler;
import io.harness.persistence.HPersistence;
import io.harness.persistence.QueryFactory;
import io.harness.persistence.UserProvider;
import io.harness.persistence.store.Store;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.annotations.PipelineServiceAuthIfHasApiKey;
import io.harness.queue.QueueListenerController;
import io.harness.queue.TimerScheduledExecutorService;
import io.harness.redis.intfc.DelegateRedissonCacheManager;
import io.harness.security.NextGenAuthenticationFilter;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.serializer.AnnotationAwareJsonSubtypeResolver;
import io.harness.serializer.KryoRegistrar;
import io.harness.service.impl.DelegateCacheImpl;
import io.harness.service.impl.DelegateTokenServiceImpl;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateProfileObserver;
import io.harness.state.inspection.StateInspectionListener;
import io.harness.state.inspection.StateInspectionServiceImpl;
import io.harness.stream.AtmosphereBroadcaster;
import io.harness.stream.GuiceObjectFactory;
import io.harness.stream.StreamModule;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.token.remote.TokenClient;
import io.harness.tracing.MongoRedisTracer;
import io.harness.waiter.NotifierScheduledExecutorService;

import software.wings.app.AuthModule;
import software.wings.app.CharsetResponseFilter;
import software.wings.app.GcpMarketplaceIntegrationModule;
import software.wings.app.IndexMigratorModule;
import software.wings.app.InspectCommand;
import software.wings.app.MainConfiguration;
import software.wings.app.MainConfiguration.AssetsConfigurationMixin;
import software.wings.app.ManagerExecutorModule;
import software.wings.app.ManagerQueueModule;
import software.wings.app.SSOModule;
import software.wings.app.SearchModule;
import software.wings.app.SignupModule;
import software.wings.app.TemplateModule;
import software.wings.app.WingsModule;
import software.wings.app.YamlModule;
import software.wings.dl.WingsPersistence;
import software.wings.exception.GenericExceptionMapper;
import software.wings.exception.JsonProcessingExceptionMapper;
import software.wings.exception.WingsExceptionMapper;
import software.wings.jersey.JsonViews;
import software.wings.jersey.KryoFeature;
import software.wings.security.ThreadLocalUserProvider;
import software.wings.security.authentication.totp.TotpModule;
import software.wings.service.impl.AccountServiceImpl;
import software.wings.service.impl.AppManifestCloudProviderPTaskManager;
import software.wings.service.impl.ApplicationManifestServiceImpl;
import software.wings.service.impl.ArtifactStreamServiceImpl;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.AuditServiceImpl;
import software.wings.service.impl.CloudProviderObserver;
import software.wings.service.impl.DelegateObserver;
import software.wings.service.impl.DelegateProfileServiceImpl;
import software.wings.service.impl.DelegateServiceImpl;
import software.wings.service.impl.InfrastructureMappingServiceImpl;
import software.wings.service.impl.SettingAttributeObserver;
import software.wings.service.impl.SettingsServiceImpl;
import software.wings.service.impl.WorkflowExecutionServiceImpl;
import software.wings.service.impl.applicationmanifest.ManifestPerpetualTaskManger;
import software.wings.service.impl.artifact.ArtifactStreamPTaskManager;
import software.wings.service.impl.artifact.ArtifactStreamSettingAttributePTaskManager;
import software.wings.service.impl.infrastructuredefinition.InfrastructureDefinitionServiceImpl;
import software.wings.service.impl.workflow.WorkflowServiceImpl;
import software.wings.service.impl.yaml.YamlPushServiceImpl;
import software.wings.service.intfc.InfrastructureDefinitionServiceObserver;
import software.wings.service.intfc.InfrastructureMappingServiceObserver;
import software.wings.service.intfc.account.AccountCrudObserver;
import software.wings.service.intfc.applicationmanifest.ApplicationManifestServiceObserver;
import software.wings.service.intfc.artifact.ArtifactStreamServiceObserver;
import software.wings.service.intfc.entitycrud.EntityCrudOperationObserver;
import software.wings.service.intfc.manipulation.SettingsServiceManipulationObserver;
import software.wings.service.intfc.perpetualtask.PerpetualTaskCrudObserver;
import software.wings.sm.StateMachineExecutor;
import software.wings.sm.StateStatusUpdate;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.github.dirkraft.dropwizard.fileassets.FileAssetsBundle;
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
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.palominolabs.metrics.guice.MetricsInstrumentationModule;
import dev.morphia.AdvancedDatastore;
import dev.morphia.converters.TypeConverter;
import io.dropwizard.Application;
import io.dropwizard.bundles.assets.AssetsConfiguration;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.cache.Cache;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletRegistration;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.cpr.BroadcasterFactory;
import org.atmosphere.cpr.MetaBroadcaster;
import org.coursera.metrics.datadog.DatadogReporter;
import org.coursera.metrics.datadog.transport.HttpTransport;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.model.Resource;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RedissonClient;
import org.reflections.Reflections;
import org.springframework.core.convert.converter.Converter;
import ru.vyarus.guice.validator.ValidationModule;

/**
 * The main application - entry point for the entire Wings Application.
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Slf4j
@OwnedBy(CDC)
public class NGMigrationApplication extends Application<MigratorConfig> {
  private final MetricRegistry metricRegistry = new MetricRegistry();
  private HarnessMetricRegistry harnessMetricRegistry;
  private StartupMode startupMode;

  public NGMigrationApplication(StartupMode startupMode) {
    this.startupMode = startupMode;
  }

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

    new NGMigrationApplication(StartupMode.MANAGER).run(args);
  }

  @Override
  public String getName() {
    return "Wings Application";
  }

  @Override
  public void initialize(Bootstrap<MigratorConfig> bootstrap) {
    initializeLogging();
    log.info("bootstrapping ...");
    bootstrap.addCommand(new InspectCommand<>(this));

    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    bootstrap.addBundle(new SwaggerBundle<MigratorConfig>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(MigratorConfig mainConfiguration) {
        return mainConfiguration.getCg().getSwaggerBundleConfiguration();
      }
    });
    bootstrap.addBundle(new FileAssetsBundle("/.well-known"));
    configureObjectMapper(bootstrap.getObjectMapper());
    bootstrap.setMetricRegistry(metricRegistry);

    log.info("bootstrapping done.");
  }

  public static void configureObjectMapper(final ObjectMapper mapper) {
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.addMixIn(AssetsConfiguration.class, AssetsConfigurationMixin.class);
    final AnnotationAwareJsonSubtypeResolver subtypeResolver =
        AnnotationAwareJsonSubtypeResolver.newInstance(mapper.getSubtypeResolver());
    mapper.setSubtypeResolver(subtypeResolver);
    mapper.setConfig(mapper.getSerializationConfig().withView(JsonViews.Public.class));
    mapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
      // defining a different serialVersionUID then base. We don't care about serializing it.
      private static final long serialVersionUID = 7777451630128399020L;

      @Override
      public List<NamedType> findSubtypes(Annotated a) {
        final List<NamedType> subtypesFromSuper = super.findSubtypes(a);
        if (isNotEmpty(subtypesFromSuper)) {
          return subtypesFromSuper;
        }
        return emptyIfNull(subtypeResolver.findSubtypes(a));
      }
    });
  }

  @Override
  public void run(final MigratorConfig configuration, Environment environment) throws Exception {
    log.info("Starting app ...");
    log.info("Entering startup maintenance mode");
    MaintenanceController.forceMaintenance(true);

    ExecutorModule.getInstance().setExecutorService(ThreadPool.create(
        20, 1000, 500L, TimeUnit.MILLISECONDS, new ThreadFactoryBuilder().setNameFormat("main-app-pool-%d").build()));

    List<Module> modules = new ArrayList<>();
    addModules(configuration.getCg(), modules);

    Module providerModule = new ProviderModule() {
      @Provides
      @Singleton
      @Named("dbAliases")
      public List<String> getDbAliases() {
        return Collections.EMPTY_LIST;
      }
    };
    modules.add(providerModule);

    modules.add(new MigratorModule(configuration));

    Injector injector = Guice.createInjector(modules);

    initializeManagerSvc(injector, environment, configuration.getCg());
    log.info("Starting app done");
    log.info("Manager is running on JRE: {}", System.getProperty("java.version"));
  }

  public void initializeManagerSvc(Injector injector, Environment environment, MainConfiguration configuration) {
    // Access all caches before coming out of maintenance
    injector.getInstance(new Key<Map<String, Cache<?, ?>>>() {});

    boolean shouldEnableDelegateMgmt = shouldEnableDelegateMgmt(configuration);
    if (shouldEnableDelegateMgmt) {
      registerAtmosphereStreams(environment, injector);
    }

    if (isManager()) {
      registerHealthChecksManager(environment, injector);
    }
    if (shouldEnableDelegateMgmt) {
      registerHealthChecksDelegateService(environment, injector);
    }

    registerStores(configuration, injector);
    if (configuration.getMongoConnectionFactory().getTraceMode() == TraceMode.ENABLED) {
      registerQueryTracer(injector);
    }

    registerResources(environment, injector);

    // Managed beans
    registerManagedBeansCommon(configuration, environment, injector);

    // common for both manager and dms
    registerCorsFilter(configuration, environment);
    registerJerseyProviders(environment, injector);
    registerCharsetResponseFilter(environment, injector);
    // Authentication/Authorization filters
    registerAuthFilters(configuration, environment, injector);
    registerCorrelationFilter(environment, injector);

    if (BooleanUtils.isTrue(configuration.getEnableOpentelemetry())) {
      registerTraceFilter(environment, injector);
    }

    environment.lifecycle().addServerLifecycleListener(server -> {
      for (Connector connector : server.getConnectors()) {
        if (connector instanceof ServerConnector) {
          ServerConnector serverConnector = (ServerConnector) connector;
          if (serverConnector.getName().equalsIgnoreCase("application")) {
            configuration.setSslEnabled(
                serverConnector.getDefaultConnectionFactory().getProtocol().equalsIgnoreCase("ssl"));
            configuration.setApplicationPort(serverConnector.getLocalPort());
            return;
          }
        }
      }
    });

    injector.getInstance(EventsModuleHelper.class).initialize();
    registerDatadogPublisherIfEnabled(configuration);

    log.info("Leaving startup maintenance mode");
    MaintenanceController.resetForceMaintenance();
  }

  private void registerQueryTracer(Injector injector) {
    AdvancedDatastore datastore = injector.getInstance(Key.get(AdvancedDatastore.class, named("primaryDatastore")));
    MongoRedisTracer tracer = injector.getInstance(MongoRedisTracer.class);
    ((QueryFactory) datastore.getQueryFactory()).getTracerSubject().register(tracer);
  }

  public boolean isManager() {
    return startupMode.equals(StartupMode.MANAGER);
  }

  public boolean shouldEnableDelegateMgmt(final MainConfiguration configuration) {
    return startupMode.equals(StartupMode.DELEGATE_SERVICE) || !configuration.isDisableDelegateMgmtInManager();
  }

  public void addModules(final MainConfiguration configuration, List<Module> modules) {
    modules.add(new ProviderModule() {
      @Provides
      @Named("delegate")
      @Singleton
      public RLocalCachedMap<String, Delegate> getDelegateCache(DelegateRedissonCacheManager cacheManager) {
        return new MockedRLocalCacheMap<>();
      }

      @Provides
      @Named("delegate_group")
      @Singleton
      public RLocalCachedMap<String, DelegateGroup> getDelegateGroupCache(DelegateRedissonCacheManager cacheManager) {
        return new MockedRLocalCacheMap<>();
      }

      @Provides
      @Named("delegates_from_group")
      @Singleton
      public RLocalCachedMap<String, List<Delegate>> getDelegatesFromGroupCache(
          DelegateRedissonCacheManager cacheManager) {
        return new MockedRLocalCacheMap<>();
      }

      @Provides
      @Named("aborted_task_list")
      @Singleton
      public RLocalCachedMap<String, Set<String>> getAbortedTaskListCache(DelegateRedissonCacheManager cacheManager) {
        return new MockedRLocalCacheMap<>();
      }

      @Provides
      @Singleton
      @Named("enableRedisForDelegateService")
      boolean isEnableRedisForDelegateService() {
        return false;
      }

      @Provides
      @Singleton
      @Named("redissonClient")
      RedissonClient redissonClient() {
        return new MockedRedissonClient();
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(CurrentGenRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(CurrentGenRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(CurrentGenRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
            .addAll(CurrentGenRegistrars.springConverters)
            .build();
      }
    });

    modules.add(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new ThreadLocalUserProvider();
      }
    });

    //    modules.add(new SpringPersistenceModule());

    ValidatorFactory validatorFactory = Validation.byDefaultProvider()
                                            .configure()
                                            .parameterNameProvider(new ReflectionParameterNameProvider())
                                            .buildValidatorFactory();

    CacheModule cacheModule = new CacheModule(configuration.getCacheConfig());
    modules.add(cacheModule);
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      AtmosphereBroadcaster atmosphereBroadcaster() {
        return configuration.getAtmosphereBroadcaster();
      }
    });
    modules.add(StreamModule.getInstance());

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(MetricRegistry.class).toInstance(metricRegistry);
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

    modules.add(FiltersModule.getInstance());
    modules.add(new ValidationModule(validatorFactory));
    modules.add(new DelegateServiceModule());
    modules.add(MigrationModule.getInstance());
    registerRemoteObserverModule(configuration, modules);
    modules.add(new WingsModule(configuration, StartupMode.MANAGER));
    modules.add(new TotpModule());
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

    modules.add(new IndexMigratorModule());
    modules.add(new YamlModule());
    modules.add(new ManagerQueueModule());

    modules.add(new ManagerExecutorModule());
    modules.add(new TemplateModule());
    modules.add(new MetricRegistryModule(metricRegistry));
    modules.add(new EventsModule(configuration));
    modules.add(GraphQLModule.getInstance());
    modules.add(new SSOModule());
    modules.add(new SignupModule());
    modules.add(new AuthModule());
    modules.add(new GcpMarketplaceIntegrationModule());
    modules.add(new SearchModule());
    modules.add(new ProviderModule() {
      @Provides
      public GrpcServerConfig getGrpcServerConfig() {
        return configuration.getGrpcServerConfig();
      }
    });
    modules.add(new GrpcServiceConfigurationModule(
        configuration.getGrpcServerConfig(), configuration.getPortal().getJwtNextGenManagerSecret()));
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      WorkersConfiguration workersConfig() {
        return configuration.getWorkers();
      }

      @Provides
      @Singleton
      PublisherConfiguration publisherConfiguration() {
        return configuration.getPublisherConfiguration();
      }
    });

    modules.add(new CommandLibraryServiceClientModule(configuration.getCommandLibraryServiceConfig()));
    modules.add(new AbstractCfModule() {
      @Override
      public CfClientConfig cfClientConfig() {
        return configuration.getCfClientConfig();
      }

      @Override
      public CfMigrationConfig cfMigrationConfig() {
        return configuration.getCfMigrationConfig();
      }

      @Override
      public FeatureFlagConfig featureFlagConfig() {
        return configuration.getFeatureFlagConfig();
      }
    });

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(DelegateCache.class).to(DelegateCacheImpl.class).in(Singleton.class);
      }
    });
  }

  private void registerAtmosphereStreams(Environment environment, Injector injector) {
    AtmosphereServlet atmosphereServlet = injector.getInstance(AtmosphereServlet.class);
    atmosphereServlet.framework().objectFactory(new GuiceObjectFactory(injector));
    injector.getInstance(BroadcasterFactory.class);
    injector.getInstance(MetaBroadcaster.class);
    ServletRegistration.Dynamic dynamic = environment.servlets().addServlet("StreamServlet", atmosphereServlet);
    dynamic.setAsyncSupported(true);
    dynamic.setLoadOnStartup(0);
    dynamic.addMapping("/stream/*");
  }

  private void registerDatadogPublisherIfEnabled(MainConfiguration configuration) {
    DatadogConfig datadogConfig = configuration.getDatadogConfig();
    if (datadogConfig != null && datadogConfig.isEnabled()) {
      try {
        log.info("Registering datadog javaagent");
        HttpTransport httpTransport = new HttpTransport.Builder().withApiKey(datadogConfig.getApiKey()).build();
        DatadogReporter reporter = DatadogReporter.forRegistry(harnessMetricRegistry.getThreadPoolMetricRegistry())
                                       .withTransport(httpTransport)
                                       .build();

        reporter.start(60, TimeUnit.SECONDS);
        log.info("Registered datadog javaagent");
      } catch (Exception t) {
        log.error("Error while initializing datadog", t);
      }
    }
  }

  private void registerHealthChecksManager(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("WingsApp", healthService);

    if (!injector.getInstance(FeatureFlagService.class).isGlobalEnabled(GLOBAL_DISABLE_HEALTH_CHECK)) {
      healthService.registerMonitor(injector.getInstance(HPersistence.class));
      healthService.registerMonitor((HealthMonitor) injector.getInstance(PersistentLocker.class));
      TimeScaleDBService timeScaleDBService = injector.getInstance(TimeScaleDBService.class);
      if (timeScaleDBService.getTimeScaleDBConfig().isHealthCheckNeeded()) {
        healthService.registerMonitor(injector.getInstance(TimeScaleDBService.class));
      }
    }
  }

  private void registerHealthChecksDelegateService(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("DelegateMgmtService", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
    healthService.registerMonitor((HealthMonitor) injector.getInstance(PersistentLocker.class));
  }

  private void registerStores(MainConfiguration configuration, Injector injector) {
    final HPersistence persistence = injector.getInstance(HPersistence.class);
    if (configuration.getDistributedLockImplementation() == DistributedLockImplementation.MONGO
        && isNotEmpty(configuration.getMongoConnectionFactory().getLocksUri())
        && !configuration.getMongoConnectionFactory().getLocksUri().equals(
            configuration.getMongoConnectionFactory().getUri())) {
      persistence.register(LOCKS_STORE, configuration.getMongoConnectionFactory().getLocksUri());
    }
    if (isNotEmpty(configuration.getEventsMongo().getUri())
        && !configuration.getEventsMongo().getUri().equals(configuration.getMongoConnectionFactory().getUri())) {
      persistence.register(Store.builder().name("events").build(), configuration.getEventsMongo().getUri());
    }
  }

  private void registerCorsFilter(MainConfiguration configuration, Environment environment) {
    FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
    String allowedOrigins = configuration.getPortal().getUrl();
    if (!configuration.getPortal().getAllowedOrigins().isEmpty()) {
      allowedOrigins = configuration.getPortal().getAllowedOrigins();
    }
    cors.setInitParameters(of("allowedOrigins", allowedOrigins, "allowedHeaders",
        "X-Requested-With,Content-Type,Accept,Origin,Authorization,X-api-key", "allowedMethods",
        "OPTIONS,GET,PUT,POST,DELETE,HEAD", "preflightMaxAge", "86400"));
    cors.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
  }

  private void registerResources(Environment environment, Injector injector) {
    Reflections reflections = new Reflections(NgMigrationResource.class.getPackage().getName());
    Set<Class<? extends Object>> resourceClasses = reflections.getTypesAnnotatedWith(Path.class);
    for (Class<?> resource : resourceClasses) {
      if (Resource.isAcceptable(resource)) {
        environment.jersey().register(injector.getInstance(resource));
      }
    }
  }

  private void registerManagedBeansCommon(MainConfiguration configuration, Environment environment, Injector injector) {
    environment.lifecycle().manage((Managed) injector.getInstance(WingsPersistence.class));
    environment.lifecycle().manage((Managed) injector.getInstance(PersistentLocker.class));
    environment.lifecycle().manage(injector.getInstance(QueueListenerController.class));
    environment.lifecycle().manage(injector.getInstance(TimerScheduledExecutorService.class));
    environment.lifecycle().manage(injector.getInstance(NotifierScheduledExecutorService.class));
    environment.lifecycle().manage((Managed) injector.getInstance(ExecutorService.class));
    environment.lifecycle().manage(injector.getInstance(MaintenanceController.class));
  }

  private void registerCorrelationFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CorrelationFilter.class));
  }

  private void registerTraceFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(TraceFilter.class));
  }

  private void registerJerseyProviders(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(KryoFeature.class));
    environment.jersey().register(EarlyEofExceptionMapper.class);
    environment.jersey().register(JsonProcessingExceptionMapper.class);
    environment.jersey().register(ConstraintViolationExceptionMapper.class);
    environment.jersey().register(WingsExceptionMapper.class);
    environment.jersey().register(GenericExceptionMapper.class);
    environment.jersey().register(MultiPartFeature.class);
  }

  private void registerAuthFilters(MainConfiguration configuration, Environment environment, Injector injector) {
    if (configuration.isEnableAuth()) {
      Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate = resourceInfoAndRequest
          -> (resourceInfoAndRequest.getKey().getResourceMethod() != null
                 && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(PipelineServiceAuth.class)
                     != null)
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
      serviceToSecretMapping.put(
          AuthorizationServiceHeader.BEARER.getServiceId(), configuration.getPortal().getJwtAuthSecret());
      serviceToSecretMapping.put(AuthorizationServiceHeader.IDENTITY_SERVICE.getServiceId(),
          configuration.getPortal().getJwtIdentityServiceSecret());
      serviceToSecretMapping.put(
          AuthorizationServiceHeader.DEFAULT.getServiceId(), configuration.getPortal().getJwtNextGenManagerSecret());
      environment.jersey().register(new NextGenAuthenticationFilter(predicate, null, serviceToSecretMapping,
          injector.getInstance(Key.get(TokenClient.class, Names.named("PRIVILEGED")))));
    }
  }

  private void registerCharsetResponseFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CharsetResponseFilter.class));
  }

  public boolean isDms() {
    return startupMode.equals(StartupMode.DELEGATE_SERVICE);
  }

  private boolean shouldEnableRemoteObservers(final MainConfiguration configuration) {
    return isDms() || configuration.isDisableDelegateMgmtInManager();
  }

  private void registerRemoteObserverModule(MainConfiguration configuration, List<Module> modules) {
    if (shouldEnableRemoteObservers(configuration)) {
      modules.add(new AbstractRemoteObserverModule() {
        @Override
        public boolean noOpProducer() {
          return false;
        }

        @Override
        public Set<RemoteObserver> observers() {
          Set<RemoteObserver> remoteObservers = new HashSet<>();
          if (isManager()) {
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(YamlPushServiceImpl.class)
                                    .observerClass(EntityCrudOperationObserver.class)
                                    .observer(AuditServiceImpl.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(AuditServiceHelper.class)
                                    .observerClass(EntityCrudOperationObserver.class)
                                    .observer(AuditServiceImpl.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(SettingsServiceImpl.class)
                                    .observerClass(CloudProviderObserver.class)
                                    .observer(ClusterRecordHandler.class)
                                    .observer(AppManifestCloudProviderPTaskManager.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(SettingsServiceImpl.class)
                                    .observerClass(SettingAttributeObserver.class)
                                    .observer(ArtifactStreamSettingAttributePTaskManager.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(InfrastructureDefinitionServiceImpl.class)
                                    .observerClass(InfrastructureDefinitionServiceObserver.class)
                                    .observer(ClusterRecordHandler.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(InfrastructureMappingServiceImpl.class)
                                    .observerClass(InfrastructureMappingServiceObserver.class)
                                    .observer(ClusterRecordHandler.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(ClusterRecordServiceImpl.class)
                                    .observerClass(ClusterRecordObserver.class)
                                    .observer(CEPerpetualTaskHandler.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(ArtifactStreamServiceImpl.class)
                                    .observerClass(ArtifactStreamServiceObserver.class)
                                    .observer(ArtifactStreamPTaskManager.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(ArtifactStreamServiceImpl.class)
                                    .observerClass(ArtifactStreamServiceObserver.class)
                                    .observer(ArtifactStreamPTaskManager.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(AccountServiceImpl.class)
                                    .observerClass(AccountCrudObserver.class)
                                    .observer(DelegateProfileServiceImpl.class)
                                    .observer(CEPerpetualTaskHandler.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(ApplicationManifestServiceImpl.class)
                                    .observerClass(ApplicationManifestServiceObserver.class)
                                    .observer(ManifestPerpetualTaskManger.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(SettingsServiceImpl.class)
                                    .observerClass(SettingsServiceManipulationObserver.class)
                                    .observer(WorkflowServiceImpl.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(StateMachineExecutor.class)
                                    .observerClass(StateStatusUpdate.class)
                                    .observer(WorkflowExecutionServiceImpl.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(StateInspectionServiceImpl.class)
                                    .observerClass(StateInspectionListener.class)
                                    .observer(StateMachineExecutor.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(DelegateServiceImpl.class)
                                    .observerClass(DelegateObserver.class)
                                    .observer(KubernetesClusterHandler.class)
                                    .build());
          }

          if (isDms()) {
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(DelegateServiceImpl.class)
                                    .observerClass(DelegateProfileObserver.class)
                                    .observer(DelegateProfileEventHandler.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(DelegateProfileServiceImpl.class)
                                    .observerClass(DelegateProfileObserver.class)
                                    .observer(DelegateProfileEventHandler.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(PerpetualTaskServiceImpl.class)
                                    .observerClass(PerpetualTaskCrudObserver.class)
                                    .observer(PerpetualTaskRecordHandler.class)
                                    .build());
            remoteObservers.add(RemoteObserver.builder()
                                    .subjectCLass(AccountServiceImpl.class)
                                    .observerClass(AccountCrudObserver.class)
                                    .observer(DelegateTokenServiceImpl.class)
                                    .build());
          }
          return remoteObservers;
        }

        @Override
        public Class<? extends RemoteObserverInformer> getRemoteObserverImpl() {
          if (isManager()) {
            return ManagerObserverEventProducer.class;
          }
          return DmsObserverEventProducer.class;
        }
      });
    } else {
      modules.add(new AbstractRemoteObserverModule() {
        @Override
        public boolean noOpProducer() {
          return true;
        }

        @Override
        public Set<RemoteObserver> observers() {
          return Collections.emptySet();
        }

        @Override
        public Class<? extends RemoteObserverInformer> getRemoteObserverImpl() {
          return NoOpRemoteObserverInformerImpl.class;
        }
      });
    }
  }
}
