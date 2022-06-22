package io.harness.ngsettings.api;

import io.harness.ng.core.setting.SettingCategory;
import io.harness.ngsettings.beans.SettingRequestDTO;
import io.harness.ngsettings.beans.SettingResponseDTO;
import io.harness.ngsettings.beans.SettingValueRequestDTO;
import io.harness.ngsettings.beans.SettingValueResponseDTO;
import io.harness.ngsettings.entities.SettingConfiguration;

import java.util.List;

public interface SettingsService {
  List<SettingResponseDTO> list(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SettingCategory category);
  List<SettingResponseDTO> update(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      List<SettingRequestDTO> settingRequestDTO);
  SettingValueResponseDTO get(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      SettingValueRequestDTO settingValueRequestDTO);
  List<SettingConfiguration> listDefaultSettings();
  void deleteConfig(String identifier);
  SettingConfiguration upsertConfig(SettingConfiguration settingConfiguration);
}
