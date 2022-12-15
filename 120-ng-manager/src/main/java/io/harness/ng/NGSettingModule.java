/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.services.SettingValidator;
import io.harness.ngsettings.services.SettingsService;
import io.harness.ngsettings.services.impl.SettingsServiceImpl;
import io.harness.ngsettings.services.impl.validators.DisableBuiltInHarnessSMValidator;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;

public class NGSettingModule extends AbstractModule {
  NextGenConfiguration appConfig;

  public NGSettingModule(NextGenConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  protected void configure() {
    bind(NextGenConfiguration.class).toInstance(appConfig);
    bind(SettingsService.class).to(SettingsServiceImpl.class);
    registerSettingValidators();
  }
  public void registerSettingValidators() {
    MapBinder<String, SettingValidator> settingValidatorMapBinder =
        MapBinder.newMapBinder(binder(), String.class, SettingValidator.class);
    settingValidatorMapBinder.addBinding(SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER)
        .to(DisableBuiltInHarnessSMValidator.class);
  }
}
