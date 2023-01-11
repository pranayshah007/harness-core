/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol;

import io.harness.metrics.MetricRegistryModule;
import io.harness.mongo.IndexManager;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import dev.morphia.AdvancedDatastore;
import io.dropwizard.Application;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;

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
  protected void run(Bootstrap<T> bootstrap, Namespace namespace, T configuration) {
    AccessControlConfiguration appConfig = (AccessControlConfiguration) configuration;
    appConfig.setMongoConfig(
        appConfig.getMongoConfig().toBuilder().indexManagerMode(IndexManager.Mode.INSPECT).build());
    MetricRegistry metricRegistry = new MetricRegistry();
    Injector injector =
        Guice.createInjector(AccessControlModule.getInstance(appConfig), new MetricRegistryModule(metricRegistry));
    injector.getInstance(Key.get(AdvancedDatastore.class, Names.named("primaryDatastore")));
  }
}
