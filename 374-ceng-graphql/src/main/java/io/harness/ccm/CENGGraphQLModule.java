/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.config.CurrencyPreferencesConfig;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CE)
public class CENGGraphQLModule extends AbstractModule {
  private final CurrencyPreferencesConfig currencyPreferencesConfig;

  public CENGGraphQLModule(final CurrencyPreferencesConfig currencyPreferencesConfig) {
    this.currencyPreferencesConfig = currencyPreferencesConfig;
  }

  @Provides
  @Singleton
  public CurrencyPreferencesConfig getCurrencyPreferencesConfiguration() {
    return this.currencyPreferencesConfig;
  }
}
