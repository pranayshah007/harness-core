/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import static java.util.Objects.isNull;

import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.ngsettings.SettingsValidatorFactory;
import io.harness.ngsettings.services.SettingEnforcementValidator;
import io.harness.ngsettings.services.SettingValidator;
import io.harness.ngsettings.services.SettingsService;
import io.harness.ngsettings.services.impl.SettingsServiceImpl;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import java.util.List;

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

    MapBinder<String, SettingEnforcementValidator> settingEnforcementValidatorMapBinder =
        MapBinder.newMapBinder(binder(), String.class, SettingEnforcementValidator.class);

    List<String> settingIdentifiers = SettingsValidatorFactory.getSettingIdentifiersWithValidators();

    settingIdentifiers.forEach(settingIdentifier -> {
      // Add custom validators to MapBinder
      Class<? extends SettingValidator> settingCustomValidator =
          SettingsValidatorFactory.getCustomValidator(settingIdentifier);
      if (!isNull(settingCustomValidator)) {
        settingValidatorMapBinder.addBinding(settingIdentifier).to(settingCustomValidator);
      }

      // Add enforcement validators to MapBinder
      FeatureRestrictionName featureRestrictionName =
          SettingsValidatorFactory.getFeatureRestrictionName(settingIdentifier);
      if (!isNull(featureRestrictionName)) {
        Class<? extends SettingEnforcementValidator> settingEnforcementValidator =
            SettingsValidatorFactory.getEnforcementValidator(settingIdentifier);
        if (!isNull(settingEnforcementValidator)) {
          settingEnforcementValidatorMapBinder.addBinding(settingIdentifier).to(settingEnforcementValidator);
        }
      }
    });
  }
}
