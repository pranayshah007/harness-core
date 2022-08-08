/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import io.harness.registrars.WalkTreeVisitorFieldRegistrar;
import io.harness.walktree.registries.VisitorRegistryModule;
import io.harness.walktree.registries.registrars.VisitableFieldRegistrar;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;

public class WalkTreeModule extends AbstractModule {
  private static volatile WalkTreeModule instance;

  public static WalkTreeModule getInstance() {
    if (instance == null) {
      instance = new WalkTreeModule();
    }
    return instance;
  }

  @Override
  protected void configure() {
    install(VisitorRegistryModule.getInstance());
    MapBinder<String, VisitableFieldRegistrar> visitableFieldRegistrarMapBinder =
        MapBinder.newMapBinder(binder(), String.class, VisitableFieldRegistrar.class);
    visitableFieldRegistrarMapBinder.addBinding(WalkTreeVisitorFieldRegistrar.class.getName())
        .to(WalkTreeVisitorFieldRegistrar.class);
  }
}
