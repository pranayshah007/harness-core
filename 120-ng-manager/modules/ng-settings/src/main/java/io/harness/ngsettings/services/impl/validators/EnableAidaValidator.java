/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.services.impl.validators;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.Boolean.parseBoolean;

import io.harness.eula.AgreementType;
import io.harness.eula.service.EulaService;
import io.harness.exception.InvalidRequestException;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.ngsettings.services.SettingValidator;
import io.harness.ngsettings.services.SettingsService;

import com.google.inject.Inject;

public class EnableAidaValidator implements SettingValidator {
  @Inject EulaService eulaService;
  @Inject SettingsService settingsService;

  @Override
  public void validate(String accountIdentifier, SettingDTO oldSetting, SettingDTO newSetting) {
    if (!parseBoolean(newSetting.getValue())) {
      return;
    }
    if (isNotEmpty(newSetting.getProjectIdentifier())) {
      validateIfSettingIsEnabledOnAccountScope(accountIdentifier, newSetting.getIdentifier());
    } else {
      validateIfEulaIsSigned(accountIdentifier, newSetting.getIdentifier());
    }
  }

  private void validateIfSettingIsEnabledOnAccountScope(String accountIdentifier, String settingIdentifier) {
    SettingValueResponseDTO responseDTO = settingsService.get(settingIdentifier, accountIdentifier, null, null);
    if (!parseBoolean(responseDTO.getValue())) {
      throw new InvalidRequestException(
          String.format("Setting %s cannot be enabled on current scope. Please first enable it on Account scope.",
              settingIdentifier));
    }
  }

  private void validateIfEulaIsSigned(String accountIdentifier, String settingIdentifier) {
    if (!eulaService.isSigned(AgreementType.AIDA, accountIdentifier)) {
      throw new InvalidRequestException(String.format(
          "Setting %s cannot be enabled on current scope. Please sign End User License Agreement to enable it.",
          settingIdentifier));
    }
  }
}
