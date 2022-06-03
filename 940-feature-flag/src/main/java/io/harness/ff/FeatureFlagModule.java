/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ff;

import io.harness.account.AccountClient;
import io.harness.account.AccountClientModule;
import io.harness.lock.PersistentLockModule;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.OptionalBinder;

public class FeatureFlagModule extends AbstractModule {
  private static volatile FeatureFlagModule instance;

  private AccountClientModule accountClientModule;

  private FeatureFlagModule() {}

  public static FeatureFlagModule getInstance() {
    if (instance == null) {
      instance = new FeatureFlagModule();
    }

    return instance;
  }

  public static FeatureFlagModule getInstance(AccountClientModule accountClientModule) {
    FeatureFlagModule featureFlagModule = getInstance();
    featureFlagModule.accountClientModule = accountClientModule;
    return featureFlagModule;
  }

  @Override
  protected void configure() {
    install(PersistentLockModule.getInstance());
    OptionalBinder.newOptionalBinder(binder(), AccountClient.class);
    bind(FeatureFlagService.class).to(FeatureFlagServiceImpl.class);
  }
}