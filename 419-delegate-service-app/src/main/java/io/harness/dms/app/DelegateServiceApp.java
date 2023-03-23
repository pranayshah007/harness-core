/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dms.app;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.ng.DbAliases.DMS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.dms.configuration.DelegateServiceConfiguration;
import io.harness.dms.health.DelegateServiceHealthResource;
import io.harness.dms.module.DelegateServiceModule;
import io.harness.dms.resource.DelegateServiceVersionInfoResource;
import io.harness.ff.FeatureFlagConfig;
import io.harness.health.HealthService;
import io.harness.mongo.MongoPersistence;
import io.harness.persistence.HPersistence;
import io.harness.persistence.store.Store;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import io.serializer.HObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.DEL)
@Slf4j
public class DelegateServiceApp extends Application<DelegateServiceConfiguration> {
  public static void main(String... args) throws Exception {
    new DelegateServiceApp().run(args);
  }

  @Override
  public String getName() {
    return "Delegate Service Application";
  }

  @Override
  public void run(DelegateServiceConfiguration delegateServiceConfig, Environment environment) throws Exception {
    log.info("Starting Delegate Service App");
    ExecutorModule.getInstance().setExecutorService(
        ThreadPool.create(delegateServiceConfig.getCommonPoolConfig().getCorePoolSize(),
            delegateServiceConfig.getCommonPoolConfig().getMaxPoolSize(),
            delegateServiceConfig.getCommonPoolConfig().getIdleTime(), TimeUnit.MILLISECONDS,
            new ThreadFactoryBuilder().setNameFormat("main-app-pool-%d").build()));

    List<Module> modules = new ArrayList<>();
    modules.add(new DelegateServiceModule(delegateServiceConfig));
    modules.add(new AbstractCfModule() {
      @Override
      public CfClientConfig cfClientConfig() {
        return delegateServiceConfig.getCfClientConfig();
      }

      @Override
      public CfMigrationConfig cfMigrationConfig() {
        return CfMigrationConfig.builder().build();
      }

      @Override
      public FeatureFlagConfig featureFlagConfig() {
        return delegateServiceConfig.getFeatureFlagConfig();
      }
    });
    Injector injector = Guice.createInjector(modules);

    registerStores(delegateServiceConfig, injector);
    registerHealthCheck(environment, injector);
    registerResources(environment, injector);
    registerAuthenticationFilter(environment, injector);
  }

  private void registerStores(DelegateServiceConfiguration configuration, Injector injector) {
    final MongoPersistence mongoPersistence = injector.getInstance(MongoPersistence.class);
    mongoPersistence.setOverrideDelegateMigration(true);
    if (isNotEmpty(configuration.getMongoConfig().getUri())) {
      mongoPersistence.register(Store.builder().name(DMS).build(), configuration.getMongoConfig().getUri());
    }
  }

  private void registerAuthenticationFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(DelegateServiceAuthFilter.class));
  }

  private void registerResources(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(DelegateServiceVersionInfoResource.class));
    environment.jersey().register(injector.getInstance(DelegateServiceHealthResource.class));
  }

  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("DelegateServiceApp", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  @Override
  public void initialize(Bootstrap<DelegateServiceConfiguration> bootstrap) {
    initializeLogging();

    log.info("bootstrapping ...");
    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    bootstrap.addBundle(new SwaggerBundle<DelegateServiceConfiguration>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(
          DelegateServiceConfiguration delegateServiceConfig) {
        return DelegateServiceSwaggerGenerator.getSwaggerBundleConfiguration();
      }
    });
    configureObjectMapper(bootstrap.getObjectMapper());
    log.info("bootstrapping done.");
  }

  public static void configureObjectMapper(final ObjectMapper mapper) {
    HObjectMapper.configureObjectMapperForNG(mapper);
  }
}
