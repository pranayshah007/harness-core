/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.eventsframework.impl.redis.GitAwareRedisProducer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.lock.redis.RedisPersistentLocker;
import io.harness.logging.LoggingInitializer;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.MetricRegistryModule;
import io.harness.queue.QueueListenerController;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.protobuf.InvalidProtocolBufferException;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.serializer.HObjectMapper;
import java.io.UnsupportedEncodingException;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.redisson.api.RedissonClient;

@Slf4j
public class EventsClientApplication extends Application<EventsClientApplicationConfiguration> {
  private static final String APPLICATION_NAME = "Events API Client Test";

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));
    new EventsClientApplication().run(args);
  }

  private final MetricRegistry metricRegistry = new MetricRegistry();

  @Override
  public String getName() {
    return APPLICATION_NAME;
  }

  @Override
  public void initialize(Bootstrap<EventsClientApplicationConfiguration> bootstrap) {
    LoggingInitializer.initializeLogging();
    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    configureObjectMapper(bootstrap.getObjectMapper());
  }
  public static void configureObjectMapper(final ObjectMapper mapper) {
    HObjectMapper.configureObjectMapperForNG(mapper);
  }

  @Override
  public void run(EventsClientApplicationConfiguration appConfig, Environment environment)
      throws InvalidProtocolBufferException, UnsupportedEncodingException, InterruptedException {
    log.info("Starting Next Gen Application ...");
    MaintenanceController.forceMaintenance(true);
    Injector injector =
        Guice.createInjector(new EventsClientApplicationModule(appConfig), new MetricRegistryModule(metricRegistry));

    registerJerseyFeatures(environment);
    registerManagedBeans(environment, injector);
    MaintenanceController.forceMaintenance(false);

    String channel = "project_update";

    RedisPersistentLocker redisLocker = injector.getInstance(RedisPersistentLocker.class);
    /* ----------------- Perform operations ----------------- */
    RedisConfig redisConfig = appConfig.getEventsFrameworkConfiguration().getRedisConfig();
    RedissonClient redissonClient = RedissonClientFactory.getClient(redisConfig);

    /* Push messages to redis channel */
    RedisProducer redisProducer =
        RedisProducer.of(channel, redissonClient, 10000, "dummyMessageProducer", redisConfig.getEnvNamespace());
    GitAwareRedisProducer gitAwareRedisProducer = GitAwareRedisProducer.of(
        channel, redissonClient, 10000, "dummyGitAwareMessageProducer", redisConfig.getEnvNamespace());
    new Thread(new MessageProducer(redisProducer, ColorConstants.TEXT_YELLOW, false)).start();
    new Thread(new MessageProducer(gitAwareRedisProducer, ColorConstants.TEXT_GREEN, true)).start();

    /* Read via Consumer groups - order is important - Sync processing usecase (Gitsync) */
    //    new Thread(new MessageConsumer(
    //                   redisLocker, redisConfig, "serialConsumerGroups", channel, "group1", 3000,
    //                   ColorConstants.TEXT_BLUE))
    //        .start();
    //    new Thread(new MessageConsumer(
    //                   redisLocker, redisConfig, "serialConsumerGroups", channel, "group1", 1000,
    //                   ColorConstants.TEXT_CYAN))
    //        .start();
    //    new Thread(new MessageConsumer(redisLocker, redisConfig, "serialConsumerGroups", channel, "group1", 500,
    //                   ColorConstants.TEXT_PURPLE))
    //        .start();

    /* Read via Consumer groups - order is not important - Load balancing usecase */
    new Thread(new MessageConsumer("consumerGroups", redissonClient, channel, "group2", 1000, ColorConstants.TEXT_BLUE,
                   redisConfig.getEnvNamespace()))
        .start();
    new Thread(new MessageConsumer("consumerGroups", redissonClient, channel, "group2", 4000,
                   ColorConstants.TEXT_PURPLE, redisConfig.getEnvNamespace()))
        .start();

    while (true) {
      Thread.sleep(10000);
    }
  }

  private void registerJerseyFeatures(Environment environment) {
    environment.jersey().register(MultiPartFeature.class);
  }

  private void registerManagedBeans(Environment environment, Injector injector) {
    environment.lifecycle().manage(injector.getInstance(QueueListenerController.class));
  }
}
