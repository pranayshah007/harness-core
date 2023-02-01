/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dms.app;

import static io.harness.logging.LoggingInitializer.initializeLogging;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.utils.DelegateServiceSwaggerGenerator;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import io.serializer.HObjectMapper;
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
    // Modules to be added as needed.
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
