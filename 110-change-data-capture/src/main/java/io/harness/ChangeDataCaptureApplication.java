/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.ff.FeatureFlagConfig;
import io.harness.govern.ProviderModule;
import io.harness.health.HealthService;
import io.harness.maintenance.MaintenanceController;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.morphia.MorphiaModule;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.Store;
import io.harness.persistence.UserProvider;
import io.harness.serializer.PersistenceRegistrars;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.Application;
import io.dropwizard.setup.Environment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.model.Resource;
import org.mongodb.morphia.converters.TypeConverter;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class ChangeDataCaptureApplication extends Application<ChangeDataCaptureServiceConfig> {
  public static final String EVENTS_DB = "events";
  public static final Store EVENTS_STORE = Store.builder().name(EVENTS_DB).build();
  public static final String CDC_DB = "change-data-capture";
  public static final Store CDC_STORE = Store.builder().name(CDC_DB).build();

  public static void main(String[] args) throws Exception {
    log.info("Starting Change Data Capture Application...");
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));
    new ChangeDataCaptureApplication().run(args);
  }

  @Override
  public void run(ChangeDataCaptureServiceConfig changeDataCaptureServiceConfig, Environment environment)
      throws Exception {
    log.info("Entering startup maintenance mode");
    MaintenanceController.forceMaintenance(true);

    ExecutorModule.getInstance().setExecutorService(ThreadPool.create(
        1, 10, 500L, TimeUnit.MILLISECONDS, new ThreadFactoryBuilder().setNameFormat("main-app-pool-%d").build()));

    List<Module> modules = new ArrayList<>();
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return changeDataCaptureServiceConfig.getHarnessMongo();
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
    modules.add(new ChangeDataCaptureModule(changeDataCaptureServiceConfig));
    modules.add(new AbstractCfModule() {
      @Override
      public CfClientConfig cfClientConfig() {
        return changeDataCaptureServiceConfig.getCfClientConfig();
      }

      @Override
      public CfMigrationConfig cfMigrationConfig() {
        return CfMigrationConfig.builder().build();
      }

      @Override
      public FeatureFlagConfig featureFlagConfig() {
        return changeDataCaptureServiceConfig.getFeatureFlagConfig();
      }
    });

    Injector injector = Guice.createInjector(modules);
    registerStores(changeDataCaptureServiceConfig, injector);
    registerManagedBeans(environment, injector);
    registerResources(environment, injector);
    registerHealthCheck(environment, injector);
    MaintenanceController.forceMaintenance(false);
  }

  private static void registerStores(ChangeDataCaptureServiceConfig config, Injector injector) {
    final String eventsMongoUri = config.getEventsMongo().getUri();
    final String cdcMongoUri = config.getCdcMongo().getUri();
    if (isNotEmpty(eventsMongoUri) && !eventsMongoUri.equals(config.getHarnessMongo().getUri())) {
      final HPersistence hPersistence = injector.getInstance(HPersistence.class);
      hPersistence.register(EVENTS_STORE, config.getEventsMongo().getUri());
    }
    if (isNotEmpty(cdcMongoUri) && !cdcMongoUri.equals(config.getHarnessMongo().getUri())) {
      final HPersistence hPersistence = injector.getInstance(HPersistence.class);
      hPersistence.register(CDC_STORE, config.getCdcMongo().getUri());
    }
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : ChangeDataCaptureServiceConfig.getResourceClasses()) {
      if (Resource.isAcceptable(resource)) {
        environment.jersey().register(injector.getInstance(resource));
      }
    }
  }

  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("Change Data Capture Application", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  private void registerManagedBeans(Environment environment, Injector injector) {
    environment.lifecycle().manage(injector.getInstance(ChangeDataCaptureSyncService.class));
  }
}
