/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform.notification;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.platform.PlatformConfiguration.NOTIFICATION_SERVICE_RESOURCES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.controller.PrimaryVersionChangeScheduler;
import io.harness.health.HealthService;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.ng.core.CorrelationFilter;
import io.harness.notification.SeedDataConfiguration;
import io.harness.notification.eventbackbone.MongoMessageConsumer;
import io.harness.notification.service.api.SeedDataPopulaterService;
import io.harness.persistence.HPersistence;
import io.harness.queue.QueueListenerController;
import io.harness.remote.CharsetResponseFilter;
import io.harness.resource.VersionInfoResource;
import io.harness.service.impl.DelegateSyncServiceImpl;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import io.dropwizard.setup.Environment;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.model.Resource;

@Slf4j
@OwnedBy(PL)
public class NotificationServiceSetup {
  public static final String NOTIFICATION_SERVICE = "NotificationService";

  public NotificationServiceSetup() {
    // sonar
  }

  public void setup(NotificationServiceConfiguration appConfig, Environment environment, Injector injector) {
    // Will create collections and Indexes
    injector.getInstance(HPersistence.class);
    registerResources(environment, injector);
    registerCharsetResponseFilter(environment, injector);
    registerCorrelationFilter(environment, injector);
    registerScheduleJobs(injector);
    registerIterators(injector);
    registerManagedBeans(environment, injector);
    registerQueueListeners(injector);
    registerHealthCheck(environment, injector);
    populateSeedData(injector, appConfig.getSeedDataConfiguration());
  }

  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("Notification Application", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  private void populateSeedData(Injector injector, SeedDataConfiguration seedDataConfiguration) {
    injector.getInstance(SeedDataPopulaterService.class).populateSeedData(seedDataConfiguration);
  }

  private void registerIterators(Injector injector) {
    //    injector.getInstance(NotificationRetryHandler.class).registerIterators();
  }

  private void registerManagedBeans(Environment environment, Injector injector) {
    environment.lifecycle().manage(injector.getInstance(QueueListenerController.class));
    environment.lifecycle().manage(
        injector.getInstance(Key.get(ManagedScheduledExecutorService.class, Names.named("delegate-response"))));
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : NOTIFICATION_SERVICE_RESOURCES) {
      if (Resource.isAcceptable(resource)) {
        environment.jersey().register(injector.getInstance(resource));
      }
    }
    environment.jersey().register(injector.getInstance(VersionInfoResource.class));
  }

  private void registerCharsetResponseFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CharsetResponseFilter.class));
  }

  private void registerCorrelationFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CorrelationFilter.class));
  }

  private void registerQueueListeners(Injector injector) {
    log.info("Initializing queue listeners...");
    QueueListenerController queueListenerController = injector.getInstance(QueueListenerController.class);
    queueListenerController.register(injector.getInstance(MongoMessageConsumer.class), 1);
  }

  private void registerScheduleJobs(Injector injector) {
    log.info("Initializing scheduled jobs...");
    injector.getInstance(PrimaryVersionChangeScheduler.class).registerExecutors();
    injector.getInstance(Key.get(ManagedScheduledExecutorService.class, Names.named("delegate-response")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateSyncServiceImpl.class), 0L, 2L, TimeUnit.SECONDS);
  }
}
