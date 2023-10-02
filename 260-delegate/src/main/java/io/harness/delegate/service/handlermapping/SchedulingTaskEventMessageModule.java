/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.handlermapping;

import io.harness.delegate.beans.SchedulingTaskEvent;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.service.handlermapping.handlers.CleanupHandler;
import io.harness.delegate.service.handlermapping.handlers.ExecutionHandler;
import io.harness.delegate.service.handlermapping.handlers.ExecutionInfrastructureHandler;
import io.harness.delegate.service.handlermapping.handlers.Handler;
import io.harness.delegate.service.runners.RunnersModule;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SchedulingTaskEventMessageModule extends AbstractModule {
  private final DelegateConfiguration configuration;
  @Override
  protected void configure() {
    MapBinder<String, Handler> mapbinder = MapBinder.newMapBinder(binder(), String.class, Handler.class);
    mapbinder.addBinding(SchedulingTaskEvent.EventType.EXECUTE.name()).to(ExecutionHandler.class);
    mapbinder.addBinding(SchedulingTaskEvent.EventType.SETUP.name()).to(ExecutionInfrastructureHandler.class);
    mapbinder.addBinding(SchedulingTaskEvent.EventType.CLEANUP.name()).to(CleanupHandler.class);
    install(new RunnersModule(configuration));
  }
}
