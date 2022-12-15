/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.app;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LoggingInitializer.initializeLogging;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.event.metrics.jobs.EventServiceRecordMetrics;
import io.harness.govern.ProviderModule;
import io.harness.health.HealthService;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.service.api.MetricService;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.morphia.MorphiaModule;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.Store;
import io.harness.persistence.UserProvider;
import io.harness.secret.ConfigSecretUtils;
import io.harness.serializer.PersistenceRegistrars;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.Application;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.protobuf.ProtobufBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.model.Resource;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.mongodb.morphia.converters.TypeConverter;
import ru.vyarus.guice.validator.ValidationModule;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class EventServiceApplication extends Application<EventServiceConfig> {
  public static final String EVENTS_DB = "events";
  public static final Store EVENTS_STORE = Store.builder().name(EVENTS_DB).build();
  private static final String APPLICATION_NAME = "CE Event Service";
  private final MetricRegistry metricRegistry = new MetricRegistry();

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));

    log.info("Starting event service application...");
    new EventServiceApplication().run(args);
  }

  @Override
  public String getName() {
    return APPLICATION_NAME;
  }

  @Override
  public void initialize(Bootstrap<EventServiceConfig> bootstrap) {
    initializeLogging();
    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(getConfigurationProvider(bootstrap.getConfigurationSourceProvider()));
    bootstrap.addBundle(getSwaggerBundle());
    bootstrap.setMetricRegistry(metricRegistry);
    bootstrap.addBundle(new ProtobufBundle<>());
  }

  private static SubstitutingSourceProvider getConfigurationProvider(ConfigurationSourceProvider sourceProvider) {
    return new SubstitutingSourceProvider(sourceProvider, new EnvironmentVariableSubstitutor(false));
  }

  private static SwaggerBundle<EventServiceConfig> getSwaggerBundle() {
    return new SwaggerBundle<EventServiceConfig>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(EventServiceConfig appConfig) {
        return appConfig.getSwaggerBundleConfiguration();
      }
    };
  }

  @Override
  public void run(EventServiceConfig configuration, Environment environment) throws Exception {
    log.info("Starting application using config: {}", configuration);
    MaintenanceController.forceMaintenance(true);
    ExecutorModule.getInstance().setExecutorService(ThreadPool.create(
        1, 10, 500L, TimeUnit.MILLISECONDS, new ThreadFactoryBuilder().setNameFormat("main-app-pool-%d").build()));

    ConfigSecretUtils.resolveSecrets(configuration.getSecretsConfiguration(), configuration);

    ValidatorFactory validatorFactory = Validation.byDefaultProvider()
                                            .configure()
                                            .parameterNameProvider(new ReflectionParameterNameProvider())
                                            .buildValidatorFactory();

    List<Module> modules = new ArrayList<>();
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      @Named("dbAliases")
      public List<String> getDbAliases() {
        return configuration.getDbAliases();
      }
    });
    modules.add(new ValidationModule(validatorFactory));
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return configuration.getHarnessMongo();
      }
    });

    modules.add(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
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

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(PersistenceRegistrars.morphiaConverters)
            .build();
      }
    });

    modules.add(MorphiaModule.getInstance());
    modules.add(new EventServiceModule(configuration));

    Injector injector = Guice.createInjector(modules);
    registerStores(configuration, injector);
    // Initialise metrics collection.
    injector.getInstance(MetricService.class).initializeMetrics();
    injector.getInstance(EventServiceRecordMetrics.class).scheduleMetricsTasks();
    initializeGrpcServer(injector);

    registerResources(environment, injector);
    registerHealthCheck(environment, injector);
    MaintenanceController.forceMaintenance(false);
  }

  private static void registerStores(EventServiceConfig config, Injector injector) {
    final String eventsMongoUri = config.getEventsMongo().getUri();
    if (isNotEmpty(eventsMongoUri) && !eventsMongoUri.equals(config.getHarnessMongo().getUri())) {
      final HPersistence hPersistence = injector.getInstance(HPersistence.class);
      hPersistence.register(EVENTS_STORE, config.getEventsMongo().getUri());
    }
  }

  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("Event Service Health Check", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : EventServiceConfig.getResourceClasses()) {
      if (Resource.isAcceptable(resource)) {
        environment.jersey().register(injector.getInstance(resource));
      }
    }
  }

  private void initializeGrpcServer(Injector injector) {
    log.info("Initializing gRPC server...");
    ServiceManager serviceManager = injector.getInstance(ServiceManager.class).startAsync();
    serviceManager.awaitHealthy();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> serviceManager.stopAsync().awaitStopped()));
  }
}
