/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform;

import io.harness.govern.ProviderModule;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.IndexManager;
import io.harness.mongo.IndexManagerInspectException;
import io.harness.mongo.MongoConfig;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.NGAuditServiceRegistrars;
import io.harness.serializer.NotificationRegistrars;
import io.harness.serializer.PrimaryVersionManagerRegistrars;
import io.harness.serializer.morphia.ResourceGroupSerializer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mongodb.MongoClientURI;
import io.dropwizard.Application;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mongodb.morphia.AdvancedDatastore;
import org.mongodb.morphia.converters.TypeConverter;

@Slf4j
public class InspectCommand<T extends io.dropwizard.Configuration> extends ConfiguredCommand<T> {
  public static final String PRIMARY_DATASTORE = "primaryDatastore";
  private final Class<T> configurationClass;

  public InspectCommand(Application<T> application) {
    super("inspect", "Parses and validates the configuration file");
    this.configurationClass = application.getConfigurationClass();
  }

  @Override
  protected Class<T> getConfigurationClass() {
    return this.configurationClass;
  }
  @Override
  protected void run(Bootstrap<T> bootstrap, Namespace namespace, T configuration) throws Exception {
    boolean isExceptionThrown = false;
    PlatformConfiguration appConfig = (PlatformConfiguration) configuration;
    List<Module> modules = new ArrayList<>();
    modules.add(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      public Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder().build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder().build();
      }
    });
    MongoClientURI uri = new MongoClientURI(appConfig.getNotificationServiceConfig().getMongoConfig().getUri());
    log.info("Database {}", uri.getDatabase());
    List<Module> notificationModules = new ArrayList<>(modules);
    notificationModules.add(getMongoConfigModule(
        appConfig.getNotificationServiceConfig().getMongoConfig(), NotificationRegistrars.morphiaRegistrars));
    Injector injector = Guice.createInjector(notificationModules);
    try {
      injector.getInstance(Key.get(AdvancedDatastore.class, Names.named(PRIMARY_DATASTORE)));
    } catch (ProvisionException e) {
      isExceptionThrown = true;
      log.error(e.getMessage());
    }

    if (appConfig.getResoureGroupServiceConfig().isEnableResourceGroup()) {
      uri = new MongoClientURI(appConfig.getResoureGroupServiceConfig().getMongoConfig().getUri());
      log.info("Database {}", uri.getDatabase());
      List<Module> resourceGroupModules = new ArrayList<>(modules);
      resourceGroupModules.add(getMongoConfigModule(
          appConfig.getResoureGroupServiceConfig().getMongoConfig(), ResourceGroupSerializer.morphiaRegistrars));
      injector = Guice.createInjector(resourceGroupModules);
      try {
        injector.getInstance(Key.get(AdvancedDatastore.class, Names.named(PRIMARY_DATASTORE)));
      } catch (ProvisionException e) {
        isExceptionThrown = true;
        log.error(e.getMessage());
      }
    }

    if (appConfig.getAuditServiceConfig().isEnableAuditService()) {
      uri = new MongoClientURI(appConfig.getAuditServiceConfig().getMongoConfig().getUri());
      log.info("Database {}", uri.getDatabase());
      List<Module> auditModules = new ArrayList<>(modules);
      auditModules.add(getMongoConfigModule(
          appConfig.getAuditServiceConfig().getMongoConfig(), NGAuditServiceRegistrars.morphiaRegistrars));
      injector = Guice.createInjector(auditModules);
      try {
        injector.getInstance(Key.get(AdvancedDatastore.class, Names.named(PRIMARY_DATASTORE)));
      } catch (ProvisionException e) {
        isExceptionThrown = true;
        log.error(e.getMessage());
      }
    }

    if (isExceptionThrown) {
      throw new IndexManagerInspectException("GENERAL_ERROR");
    }
  }

  private Module getMongoConfigModule(
      MongoConfig mongoConfig, ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars) {
    return new AbstractModule() {
      @Provides
      @Singleton
      @Named("morphiaClasses")
      Map<Class, String> morphiaCustomCollectionNames() {
        return ImmutableMap.<Class, String>builder().build();
      }

      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return mongoConfig.toBuilder().indexManagerMode(IndexManager.Mode.INSPECT).build();
      }

      @Provides
      @Singleton
      public Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(morphiaRegistrars)
            .addAll(PrimaryVersionManagerRegistrars.morphiaRegistrars)
            .build();
      }
    };
  }
}
