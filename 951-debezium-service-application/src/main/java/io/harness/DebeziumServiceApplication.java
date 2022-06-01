package io.harness;

import io.harness.debezium.DebeziumEngineStarter;
import io.harness.debezium.RedisStreamChangeConsumer;
import io.harness.maintenance.MaintenanceController;

import io.dropwizard.Application;
import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DebeziumServiceApplication extends Application<io.harness.DebeziumServiceConfiguration> {
  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));

    new DebeziumServiceApplication().run(args);
  }

  @Override
  public void run(io.harness.DebeziumServiceConfiguration appConfig, Environment environment) throws Exception {
    if (appConfig.getDebeziumConfig() != null && appConfig.getDebeziumConfig().isEnabled()) {
      DebeziumEngineStarter.startDebeziumEngine(appConfig.getDebeziumConfig(),
          new RedisStreamChangeConsumer(appConfig.getDebeziumConfig().getCollectionIncludeList(),
              appConfig.getDebeziumConfig().getOffsetStorageFileName(),
              appConfig.getDebeziumConfig().getMongodbName()));
    }
  }
}