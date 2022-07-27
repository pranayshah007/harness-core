/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import io.harness.registrars.OrchestrationBeansTimeoutRegistrar;
import io.harness.registries.registrar.TimeoutRegistrar;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrchestrationBeansModule extends AbstractModule {
  private static OrchestrationBeansModule instance;

  public static OrchestrationBeansModule getInstance() {
    if (instance == null) {
      instance = new OrchestrationBeansModule();
    }
    return instance;
  }

  @Override
  protected void configure() {
    install(TimeoutEngineModule.getInstance());

    MapBinder<String, TimeoutRegistrar> timeoutRegistrarMapBinder =
        MapBinder.newMapBinder(binder(), String.class, TimeoutRegistrar.class);
    timeoutRegistrarMapBinder.addBinding(OrchestrationBeansTimeoutRegistrar.class.getName())
        .to(OrchestrationBeansTimeoutRegistrar.class);
  }
}
