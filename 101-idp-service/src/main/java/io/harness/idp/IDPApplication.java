/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp;

import com.codahale.metrics.MetricRegistry;
import com.github.dirkraft.dropwizard.fileassets.FileAssetsBundle;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;
import dev.morphia.AdvancedDatastore;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.StartupMode;
import io.harness.ff.FeatureFlagService;
import io.harness.health.HealthMonitor;
import io.harness.health.HealthService;
import io.harness.lock.PersistentLocker;
import io.harness.maintenance.MaintenanceController;
import io.harness.persistence.HPersistence;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;
import io.harness.timescaledb.TimeScaleDBService;
import lombok.extern.slf4j.Slf4j;
import software.wings.app.InspectCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.beans.FeatureName.GLOBAL_DISABLE_HEALTH_CHECK;
import static io.harness.logging.LoggingInitializer.initializeLogging;

/**
 * The main application - entry point for the entire Wings Application.
 */
@Slf4j
@OwnedBy(IDP)
public class IDPApplication extends Application<IDPConfiguration> {
  private final MetricRegistry metricRegistry = new MetricRegistry();
  private StartupMode startupMode;

  public IDPApplication(StartupMode startupMode) {
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

    new IDPApplication(StartupMode.MANAGER).run(args);
  }

  @Override
  public String getName() {
    return "IDP Service";
  }

  @Override
  public void initialize(Bootstrap<IDPConfiguration> bootstrap) {
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
  public void run(final IDPConfiguration configuration, Environment environment) throws Exception {
    log.info("Starting app ...");
    log.info("Entering startup maintenance mode");
    MaintenanceController.forceMaintenance(true);

    ExecutorModule.getInstance().setExecutorService(ThreadPool.create(
            20, 1000, 500L, TimeUnit.MILLISECONDS, new ThreadFactoryBuilder().setNameFormat("main-app-pool-%d").build()));

    List<Module> modules = new ArrayList<>();
    modules.add(new IDPModule(configuration));
    Injector injector = Guice.createInjector(modules);
    if (isManager()) {
      registerHealthChecksManager(environment, injector);
    }
    log.info("Starting app done");
    log.info("IDP Service is running on JRE: {}", System.getProperty("java.version"));
  }

  public boolean isManager() {
    return startupMode.equals(StartupMode.MANAGER);
  }

  private void registerHealthChecksManager(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("IDP Service", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }
}
