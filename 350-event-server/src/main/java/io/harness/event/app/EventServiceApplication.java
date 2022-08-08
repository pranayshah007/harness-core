/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.app;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.event.metrics.jobs.EventServiceRecordMetrics;
import io.harness.govern.ProviderModule;
import io.harness.metrics.service.api.MetricService;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.morphia.MorphiaModule;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.Store;
import io.harness.persistence.UserProvider;
import io.harness.secret.ConfigSecretUtils;
import io.harness.serializer.PersistenceRegistrars;
import io.harness.serializer.YamlUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.LogManager;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.mongodb.morphia.converters.TypeConverter;
import org.slf4j.bridge.SLF4JBridgeHandler;
import ru.vyarus.guice.validator.ValidationModule;

@Slf4j
public class EventServiceApplication {
  public static final String EVENTS_DB = "events";
  public static final Store EVENTS_STORE = Store.builder().name(EVENTS_DB).build();

  private final EventServiceConfig config;

  private EventServiceApplication(EventServiceConfig config) {
    this.config = config;
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    // Optionally remove existing handlers attached to j.u.l root logger
    SLF4JBridgeHandler.removeHandlersForRootLogger(); // (since SLF4J 1.6.5)

    // add SLF4JBridgeHandler to j.u.l's root logger, should be done once during
    // the initialization phase of your application
    SLF4JBridgeHandler.install();

    // Set logging level
    java.util.logging.LogManager.getLogManager().getLogger("").setLevel(Level.INFO);

    log.info("Starting event service application...");

    File configFile = new File(args[0]);
    if (args.length == 2) {
      configFile = new File(args[1]);
    }
    EventServiceConfig config =
        new YamlUtils().read(FileUtils.readFileToString(configFile, UTF_8), EventServiceConfig.class);
    ConfigSecretUtils.resolveSecrets(config.getSecretsConfiguration(), config);
    new EventServiceApplication(config).run();
  }

  private void run() throws InterruptedException {
    log.info("Starting application using config: {}", config);
    ValidatorFactory validatorFactory = Validation.byDefaultProvider()
                                            .configure()
                                            .parameterNameProvider(new ReflectionParameterNameProvider())
                                            .buildValidatorFactory();

    List<Module> modules = new ArrayList<>();
    modules.add(new ValidationModule(validatorFactory));
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return config.getHarnessMongo();
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
    modules.add(new EventServiceModule(config));

    Injector injector = Guice.createInjector(modules);
    registerStores(config, injector);
    // Initialise metrics collection.
    injector.getInstance(MetricService.class).initializeMetrics();
    injector.getInstance(EventServiceRecordMetrics.class).scheduleMetricsTasks();

    ServiceManager serviceManager = injector.getInstance(ServiceManager.class).startAsync();
    serviceManager.awaitHealthy();
    log.info("Server startup complete");
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutting down server...");
      serviceManager.stopAsync().awaitStopped();
    }));
    serviceManager.awaitStopped();
    log.info("Server shutdown complete");
    LogManager.shutdown();
  }

  private static void registerStores(EventServiceConfig config, Injector injector) {
    final String eventsMongoUri = config.getEventsMongo().getUri();
    if (isNotEmpty(eventsMongoUri) && !eventsMongoUri.equals(config.getHarnessMongo().getUri())) {
      final HPersistence hPersistence = injector.getInstance(HPersistence.class);
      hPersistence.register(EVENTS_STORE, config.getEventsMongo().getUri());
    }
  }
}
