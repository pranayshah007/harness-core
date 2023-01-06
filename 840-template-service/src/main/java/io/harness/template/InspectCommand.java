/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.TemplateServiceConfiguration;
import io.harness.annotations.dev.OwnedBy;
import io.harness.govern.ProviderModule;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.IndexManager;
import io.harness.mongo.MongoConfig;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.TemplateServiceModuleRegistrars;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
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
import dev.morphia.AdvancedDatastore;
import dev.morphia.converters.TypeConverter;
import io.dropwizard.Application;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.sourceforge.argparse4j.inf.Namespace;
import org.springframework.core.convert.converter.Converter;

@OwnedBy(CDC)
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
    TemplateServiceConfiguration appConfig = (TemplateServiceConfiguration) configuration;
    appConfig.setMongoConfig(
        appConfig.getMongoConfig().toBuilder().indexManagerMode(IndexManager.Mode.INSPECT).build());
    MetricRegistry metricRegistry = new MetricRegistry();

    List<Module> modules = new ArrayList<>();
    modules.add(new AbstractModule() {
      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return appConfig.getMongoConfig();
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
            .addAll(TemplateServiceModuleRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      public Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(TemplateServiceModuleRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      public Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(TemplateServiceModuleRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
            .addAll(TemplateServiceModuleRegistrars.springConverters)
            .build();
      }
      @Provides
      @Singleton
      @Named("dbAliases")
      public List<String> getDbAliases() {
        return appConfig.getDbAliases();
      }
    });

    Injector injector = Guice.createInjector(modules);
    injector.getInstance(Key.get(AdvancedDatastore.class, Names.named("primaryDatastore")));
  }
}
