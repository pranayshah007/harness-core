/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.signup;

import static io.harness.lock.DistributedLockImplementation.NOOP;

import static org.mockito.Mockito.mock;

import io.harness.AccessControlClientConfiguration;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.concurrent.HTimeLimiter;
import io.harness.factory.ClosingFactory;
import io.harness.govern.ProviderModule;
import io.harness.lock.DistributedLockImplementation;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoModule;
import io.harness.mongo.MongoPersistence;
import io.harness.mongo.index.migrator.Migrator;
import io.harness.notification.MongoBackendConfiguration;
import io.harness.notification.NotificationClientConfiguration;
import io.harness.notification.module.NotificationClientModule;
import io.harness.notification.module.NotificationClientPersistenceModule;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.redis.RedisConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.InjectorRuleMixin;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.RbacCoreRegistrars;
import io.harness.telemetry.AbstractTelemetryModule;
import io.harness.telemetry.TelemetryConfiguration;
import io.harness.telemetry.segment.SegmentConfiguration;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoClients;
import com.mongodb.lang.Nullable;
import dev.morphia.AdvancedDatastore;
import dev.morphia.Morphia;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

@OwnedBy(HarnessTeam.GTM)
@Slf4j
public class SignupTestRule implements InjectorRuleMixin, MethodRule, MongoRuleMixin {
  @Override
  public List<Module> modules(List<Annotation> annotations) {
    MongoConfig mongoConfig = MongoConfig.builder().build();

    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());
    List<Module> modules = new ArrayList<>();

    MongoClientURI clientUri = new MongoClientURI(
        "mongodb://localhost:7457", MongoClientOptions.builder(MongoModule.getDefaultMongoClientOptions(mongoConfig)));
    String dbName = clientUri.getDatabase();

    MongoClient mongoClient = new MongoClient(new MongoClientURI("mongodb://localhost:7457"));

    com.mongodb.client.MongoClient newMongoClient = MongoClients.create("mongodb://localhost:7457");

    modules.add(new SignupModule(ServiceHttpClientConfig.builder().baseUrl("http://localhost:7457/").build(),
        "test_secret", "Service", SignupNotificationConfiguration.builder().build(),
        AccessControlClientConfiguration.builder().build(), SignupDomainDenylistConfiguration.builder().build()));

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> registrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder().addAll(RbacCoreRegistrars.kryoRegistrars).build();
      }

      @Provides
      @Named("primaryDatastore")
      @Singleton
      AdvancedDatastore datastore(Morphia morphia) {
        return (AdvancedDatastore) morphia.createDatastore(mongoClient, "dbName");
      }

      @Provides
      @Named("primaryMongoClient")
      @Singleton
      MongoClient mongoClient() {
        return mongoClient;
      }

      @Provides
      @Named("primaryMongoClient")
      @Singleton
      com.mongodb.client.MongoClient newMongoClient() {
        return newMongoClient;
      }

      @Provides
      @Singleton
      @Nullable
      UserProvider userProvider() {
        return new NoopUserProvider();
      }

      @Provides
      @Named("lock")
      @Singleton
      RedisConfig redisLockConfig() {
        return RedisConfig.builder().build();
      }

      @Provides
      @Singleton
      DistributedLockImplementation distributedLockImplementation() {
        return NOOP;
      }

      @Provides
      @Named("locksMongoClient")
      @Singleton
      public MongoClient locksMongoClient(ClosingFactory closingFactory) {
        return mongoClient;
      }

      @Provides
      @Named("locksDatabase")
      @Singleton
      String databaseNameProvider() {
        return dbName;
      }
    });

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(HPersistence.class).to(MongoPersistence.class);
        MapBinder.newMapBinder(binder(), String.class, Migrator.class);
        bind(AccountService.class).toInstance(mock(AccountService.class));
        bind(TimeLimiter.class).toInstance(HTimeLimiter.create());
      }
    });
    modules.add(new AbstractTelemetryModule() {
      @Override
      public TelemetryConfiguration telemetryConfiguration() {
        return SegmentConfiguration.builder().build();
      }
    });
    MongoBackendConfiguration mongoBackendConfiguration = MongoBackendConfiguration.builder().build();
    mongoBackendConfiguration.setType("MONGO");
    modules.add(new NotificationClientModule(NotificationClientConfiguration.builder()
                                                 .notificationClientBackendConfiguration(mongoBackendConfiguration)
                                                 .build()));
    modules.add(new NotificationClientPersistenceModule());
    return modules;
  }

  @Override
  public Statement apply(Statement base, FrameworkMethod method, Object target) {
    return applyInjector(log, base, method, target);
  }
}
