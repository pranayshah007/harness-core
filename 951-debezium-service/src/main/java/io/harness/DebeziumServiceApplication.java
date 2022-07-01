/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.logging.LoggingInitializer.initializeLogging;

import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.debezium.ChangeConsumerConfig;
import io.harness.debezium.ConsumerType;
import io.harness.debezium.DebeziumConfig;
import io.harness.debezium.DebeziumControllerStarter;
import io.harness.ff.FeatureFlagConfig;
import io.harness.lock.PersistentLocker;
import io.harness.maintenance.MaintenanceController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.serializer.HObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DebeziumServiceApplication extends Application<DebeziumServiceConfiguration> {
  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));

    new DebeziumServiceApplication().run(args);
  }

  @Override
  public void initialize(Bootstrap<DebeziumServiceConfiguration> bootstrap) {
    initializeLogging();
    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    configureObjectMapper(bootstrap.getObjectMapper());
  }

  public static void configureObjectMapper(final ObjectMapper mapper) {
    HObjectMapper.configureObjectMapperForNG(mapper);
  }

  @Override
  public void run(DebeziumServiceConfiguration appConfig, Environment environment) throws Exception {
    log.info("Starting Debezium Service Application ...");
    List<Module> modules = new ArrayList<>();
    modules.add(new AbstractCfModule() {
      @Override
      public CfClientConfig cfClientConfig() {
        return appConfig.getCfClientConfig();
      }

      @Override
      public CfMigrationConfig cfMigrationConfig() {
        return CfMigrationConfig.builder().build();
      }

      @Override
      public FeatureFlagConfig featureFlagConfig() {
        return appConfig.getFeatureFlagConfig();
      }
    });
    DebeziumServiceModuleConfig moduleConfig =
        DebeziumServiceModuleConfig.builder()
            .lockImplementation(appConfig.getDistributedLockImplementation())
            .redisLockConfig(appConfig.getRedisLockConfig())
            .eventsFrameworkConfiguration(appConfig.getEventsFrameworkConfiguration())
            .build();
    modules.add(DebeziumServiceModule.getInstance(moduleConfig));

    Injector injector = Guice.createInjector(modules);
    PersistentLocker locker = injector.getInstance(PersistentLocker.class);
    DebeziumControllerStarter starter = injector.getInstance(DebeziumControllerStarter.class);

    for (DebeziumConfig debeziumConfig : appConfig.getDebeziumConfigs()) {
      if (debeziumConfig.isEnabled()) {
        ChangeConsumerConfig changeConsumerConfig =
            ChangeConsumerConfig.builder()
                .consumerType(ConsumerType.EVENTS_FRAMEWORK)
                .eventsFrameworkConfiguration(appConfig.getEventsFrameworkConfiguration())
                .build();
        starter.startDebeziumController(debeziumConfig, changeConsumerConfig, locker, appConfig.getRedisLockConfig());
      }
    }
  }
}
