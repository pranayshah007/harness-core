package io.harness.ngsettings.services;

import io.harness.ngsettings.dto.SettingDTO;

public interface SettingValidator {
  void validate(String accountIdentifier, SettingDTO oldSetting, SettingDTO newSetting);
}
