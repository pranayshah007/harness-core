/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.utils;

import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.ngsettings.services.SettingEnforcementValidator;
import io.harness.ngsettings.services.SettingValidator;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SettingValidatorRegistrar {
  FeatureRestrictionName featureRestrictionName;
  Class<? extends SettingEnforcementValidator> settingEnforcementValidator;
  Class<? extends SettingValidator> settingValidator;

  public SettingValidatorRegistrar(FeatureRestrictionName featureRestrictionName,
      Class<? extends SettingEnforcementValidator> settingEnforcementValidator,
      Class<? extends SettingValidator> settingValidator) {
    this.featureRestrictionName = featureRestrictionName;
    this.settingEnforcementValidator = settingEnforcementValidator;
    this.settingValidator = settingValidator;
  }

  public SettingValidatorRegistrar(FeatureRestrictionName featureRestrictionName,
      Class<? extends SettingEnforcementValidator> settingEnforcementValidator) {
    this.featureRestrictionName = featureRestrictionName;
    this.settingEnforcementValidator = settingEnforcementValidator;
  }

  public SettingValidatorRegistrar(Class<? extends SettingValidator> settingValidator) {
    this.settingValidator = settingValidator;
  }
}
