/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.app;

import static io.harness.logging.LoggingInitializer.initializeLogging;

import io.harness.health.HealthService;
import io.harness.maintenance.MaintenanceController;
import io.harness.ng.core.exceptionmappers.GenericExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.JerseyViolationExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.NotAllowedExceptionMapper;
import io.harness.ng.core.exceptionmappers.NotFoundExceptionMapper;
import io.harness.ng.core.exceptionmappers.WingsExceptionMapperV2;
import io.harness.persistence.HPersistence;
import io.harness.reflection.HarnessReflections;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dirkraft.dropwizard.fileassets.FileAssetsBundle;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import io.serializer.HObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.server.model.Resource;

@Slf4j
public class AssessmentServiceApplication extends Application<AssessmentServiceConfiguration> {
  public static final Collection<Class<?>> HARNESS_RESOURCE_CLASSES = getResourceClasses();

  private final MetricRegistry metricRegistry = new MetricRegistry();

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));

    new AssessmentServiceApplication().run(args);
  }

  @Override
  public void initialize(Bootstrap<AssessmentServiceConfiguration> bootstrap) {
    initializeLogging();
    log.info("bootstrapping ...");
    bootstrap.addCommand(new InspectCommand<>(this));
    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    configureObjectMapper(bootstrap.getObjectMapper()); // there is a NG variant also TODO
    bootstrap.addBundle(new SwaggerBundle<AssessmentServiceConfiguration>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(
          AssessmentServiceConfiguration assessmentServiceConfiguration) {
        return assessmentServiceConfiguration.getSwaggerBundleConfiguration();
      }
    });
    bootstrap.addBundle(new FileAssetsBundle("/.well-known")); // check whats the use.
    // TODO step to attach metric registry
    bootstrap.setMetricRegistry(metricRegistry);
    log.info("bootstrapping done.");
  }

  public static void configureObjectMapper(final ObjectMapper mapper) {
    HObjectMapper.configureObjectMapperForNG(mapper);
  }

  @Override
  public String getName() {
    return "Assessment Service";
  }

  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("Assessment Service", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : HARNESS_RESOURCE_CLASSES) {
      if (Resource.isAcceptable(resource)) {
        environment.jersey().register(injector.getInstance(resource));
      }
    }
  }

  public static Collection<Class<?>> getResourceClasses() {
    return HarnessReflections.get()
        .getTypesAnnotatedWith(Path.class)
        .stream()
        .filter(klazz -> StringUtils.startsWithAny(klazz.getPackage().getName(), "io.harness"))
        .collect(Collectors.toSet());
  }

  private void registerExceptionMappers(JerseyEnvironment jersey) {
    jersey.register(JerseyViolationExceptionMapperV2.class);
    jersey.register(GenericExceptionMapperV2.class);
    jersey.register(new JsonProcessingExceptionMapper(true));
    jersey.register(EarlyEofExceptionMapper.class);
    jersey.register(WingsExceptionMapperV2.class);
    jersey.register(NotFoundExceptionMapper.class);
    jersey.register(NotAllowedExceptionMapper.class);
  }

  // TODO there can be a step to register auth filters etc.

  // TODO lot of other things can be registered if needded later.

  @Override
  public void run(AssessmentServiceConfiguration appConfig, Environment environment) throws Exception {
    log.info("Starting app ...");
    log.info("Entering startup maintenance mode");
    MaintenanceController.forceMaintenance(true);
    ExecutorModule.getInstance().setExecutorService(ThreadPool.create(
        20, 1000, 500L, TimeUnit.MILLISECONDS, new ThreadFactoryBuilder().setNameFormat("main-app-pool-%d").build()));
    List<Module> modules = new ArrayList<>();
    modules.add(new AssessmentServiceModule(appConfig));
    Injector injector = Guice.createInjector(modules);
    registerResources(environment, injector);
    registerHealthCheck(environment, injector);
    registerExceptionMappers(environment.jersey());
    log.info("Starting app done");
    log.info("Assessment Service is running on JRE: {}", System.getProperty("java.version"));
  }

  // register exception mappers
}
