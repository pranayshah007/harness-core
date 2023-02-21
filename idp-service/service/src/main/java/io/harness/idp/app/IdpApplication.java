/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.app;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.idp.app.IdpConfiguration.HARNESS_RESOURCE_CLASSES;
import static io.harness.logging.LoggingInitializer.initializeLogging;

import io.harness.annotations.dev.OwnedBy;
import io.harness.events.consumers.EntityCrudStreamConsumer;
import io.harness.events.consumers.IdpEventConsumerController;
import io.harness.health.HealthService;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.service.api.MetricService;
import io.harness.persistence.HPersistence;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;

import com.codahale.metrics.MetricRegistry;
import com.github.dirkraft.dropwizard.fileassets.FileAssetsBundle;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ServerProperties;

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

  @Override
  public String getName() {
    return "IDP Service";
  }

  @Override
  public void initialize(Bootstrap<IdpConfiguration> bootstrap) {
    initializeLogging();
    log.info("bootstrapping ...");
    bootstrap.addCommand(new InspectCommand<>(this));

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
    Injector injector = Guice.createInjector(modules);
    registerResources(environment, injector);
    registerHealthChecksManager(environment, injector);
    registerQueueListeners(injector);
    initMetrics(injector);
    log.info("Starting app done");
    log.info("IDP Service is running on JRE: {}", System.getProperty("java.version"));
  }

  private void registerQueueListeners(Injector injector) {
    log.info("Initializing queue listeners...");
    IdpEventConsumerController controller = injector.getInstance(IdpEventConsumerController.class);
    controller.register(injector.getInstance(EntityCrudStreamConsumer.class), 1);
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
    environment.jersey().property(ServerProperties.RESOURCE_VALIDATION_DISABLE, true);
  }
}
