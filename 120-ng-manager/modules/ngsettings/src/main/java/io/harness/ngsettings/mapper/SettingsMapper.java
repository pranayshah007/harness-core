package io.harness.ngsettings.mapper;

import io.harness.ngsettings.beans.SettingDTO;
import io.harness.ngsettings.beans.SettingResponseDTO;
import io.harness.ngsettings.entities.Setting;
import io.harness.ngsettings.entities.SettingConfiguration;

public class SettingsMapper {
  public SettingDTO getSettingDTO(SettingConfiguration settingConfiguration) {
    return SettingDTO.builder()
        .identifier(settingConfiguration.getIdentifier())
        .orgIdentifier(null)
        .projectIdentifier(null)
        .allowedValues(settingConfiguration.getAllowedValues())
        .allowOverrides(settingConfiguration.getAllowOverrides())
        .category(settingConfiguration.getCategory())
        .valueType(settingConfiguration.getValueType())
        .defaultValue(settingConfiguration.getDefaultValue())
        .value(settingConfiguration.getDefaultValue())
        .build();
  }

  public Setting getSetting(SettingResponseDTO settingResponseDTO, String accountIdentifier) {
    return Setting.builder()
        .identifier(settingResponseDTO.getSetting().getIdentifier())
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(settingResponseDTO.getSetting().getOrgIdentifier())
        .projectIdentifier(settingResponseDTO.getSetting().getProjectIdentifier())
        .category(settingResponseDTO.getSetting().getCategory())
        .allowOverrides(settingResponseDTO.getSetting().getAllowOverrides())
        .value(settingResponseDTO.getSetting().getValue())
        .lastModifiedAt(settingResponseDTO.getLastModifiedAt())
        .build();
  }
}
