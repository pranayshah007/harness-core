/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ff;

import io.harness.lock.PersistentLockModule;

import com.google.inject.AbstractModule;

public class FeatureFlagModule extends AbstractModule {
  private static volatile FeatureFlagModule instance;

  private FeatureFlagModule() {}

  public static FeatureFlagModule getInstance() {
    if (instance == null) {
      instance = new FeatureFlagModule();
    }

    return instance;
  }

  @Override
  protected void configure() {
    install(PersistentLockModule.getInstance());
    bind(FeatureFlagService.class).to(FeatureFlagServiceImpl.class);
  }
}
