/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.ngsettings.services.SettingEnforcementValidator;
import io.harness.ngsettings.services.SettingValidator;
import io.harness.ngsettings.services.impl.validators.DisableBuiltInHarnessSMValidator;
import io.harness.ngsettings.utils.SettingValidatorRegistrar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(PL)
public class SettingsValidatorFactory {
  private static Map<String, SettingValidatorRegistrar> registrar = new HashMap<>();

  static {
    registrar.put(SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER,
        new SettingValidatorRegistrar(DisableBuiltInHarnessSMValidator.class));
  }

  public static List<String> getSettingIdentifiersWithValidators() {
    return new ArrayList<>(registrar.keySet());
  }

  public static FeatureRestrictionName getFeatureRestrictionName(String settingIdentifier) {
    if (!registrar.containsKey(settingIdentifier)) {
      return null;
    }
    return registrar.get(settingIdentifier).getFeatureRestrictionName();
  }

  public static Class<? extends SettingValidator> getCustomValidator(String settingIdentifier) {
    if (!registrar.containsKey(settingIdentifier)) {
      return null;
    }
    return registrar.get(settingIdentifier).getSettingValidator();
  }

  public static Class<? extends SettingEnforcementValidator> getEnforcementValidator(String settingIdentifier) {
    if (!registrar.containsKey(settingIdentifier)) {
      return null;
    }
    return registrar.get(settingIdentifier).getSettingEnforcementValidator();
  }
}
