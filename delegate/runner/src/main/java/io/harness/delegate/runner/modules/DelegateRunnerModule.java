/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.runner.modules;

import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import io.harness.delegate.beans.DelegateTaskPackage;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.runner.DelegateRunnerCommand;
import io.harness.delegate.runner.DelegateRunnerCommandImpl;
import io.harness.delegate.runner.config.Configuration;
import io.harness.delegate.runner.config.ConfigurationProvider;
import io.harness.delegate.runner.config.DelegateConfigurationProvider;
import io.harness.delegate.runner.taskloader.DelegateTaskPackageProvider;
import io.harness.delegate.runner.taskloader.TaskPackageReader;
import io.harness.delegate.runner.taskloader.TaskPackageReaderImpl;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.KryoSerializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class DelegateRunnerModule extends AbstractModule {
  private final String configFileName;
  @Override
  protected void configure() {
    log.info(configFileName);
    install(new DelegateRunnerTasksModule());
    install(new DelegateManagerClientModule());
    install(new DelegateRunnerKryoModule());
    bind(Configuration.class).toProvider(new ConfigurationProvider(configFileName)).in(Singleton.class);
    bind(DelegateConfiguration.class).toProvider(new DelegateConfigurationProvider()).in(Singleton.class);
    bind(DelegateRunnerCommand.class).to(DelegateRunnerCommandImpl.class).in(Singleton.class);
    bind(TaskPackageReader.class).to(TaskPackageReaderImpl.class).in(Singleton.class);
    bind(DelegateTaskPackage.class).toProvider(DelegateTaskPackageProvider.class).asEagerSingleton();
  }

  @Provides
  @Named("referenceFalseKryoSerializer")
  @Singleton
  public KryoSerializer getKryoSerializer(Provider<Set<Class<? extends KryoRegistrar>>> provider) {
    return new KryoSerializer(provider.get(), false, false);
  }

}
