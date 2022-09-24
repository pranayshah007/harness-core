/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.govern.ProviderModule;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.IndexManager;
import io.harness.mongo.MongoConfig;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.serializer.CvNextGenRegistrars;
import io.harness.serializer.KryoRegistrar;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.dropwizard.Application;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mongodb.morphia.AdvancedDatastore;
import org.mongodb.morphia.converters.TypeConverter;

public class InspectCommand<T extends io.dropwizard.Configuration> extends ConfiguredCommand<T> {
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
    VerificationConfiguration mainConfiguration = (VerificationConfiguration) configuration;
    mainConfiguration.setMongoConnectionFactory(
        mainConfiguration.getMongoConnectionFactory().toBuilder().indexManagerMode(IndexManager.Mode.INSPECT).build());

    List<Module> modules = new ArrayList<>();
    modules.add(new AbstractModule() {
      @Provides
      @Singleton
      @Named("morphiaClasses")
      Map<Class, String> morphiaCustomCollectionNames() {
        return ImmutableMap.<Class, String>builder()
            .put(DelegateSyncTaskResponse.class, "cvng_delegateSyncTaskResponses")
            .put(DelegateAsyncTaskResponse.class, "cvng_delegateAsyncTaskResponses")
            .put(DelegateTaskProgressResponse.class, "cvng_delegateTaskProgressResponses")
            .build();
      }

      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return mainConfiguration.getMongoConnectionFactory();
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
      public Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(CvNextGenRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      public Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(CvNextGenRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      public Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(CvNextGenRegistrars.morphiaConverters)
            .build();
      }
    });

    Injector injector = Guice.createInjector(modules);
    injector.getInstance(Key.get(AdvancedDatastore.class, Names.named("primaryDatastore")));
  }
}
