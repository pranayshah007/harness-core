package io.harness.ngsettings.mapper;

import com.google.inject.Inject;
import io.harness.ngsettings.beans.SettingDTO;
import io.harness.ngsettings.beans.SettingResponseDTO;
import io.harness.ngsettings.entities.Setting;
import io.harness.ngsettings.entities.SettingConfiguration;
import io.harness.ngsettings.services.impl.SettingsServiceImpl;
import io.harness.repositories.SettingConfigurationRepository;

import java.util.Optional;

public class SettingsMapper {
  @Inject
  SettingConfigurationRepository settingConfigurationRepository;

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

  public SettingResponseDTO settingtoSettingResponseDTO(Setting setting) {
    Optional<SettingConfiguration> settingConfiguration = settingConfigurationRepository.findByCategoryAndIdentifier(setting.getCategory(), setting.getIdentifier());
    if(settingConfiguration.isPresent()) {
      SettingDTO settingDTO = getSettingDTO(settingConfiguration.get());
      settingDTO.setAllowOverrides(setting.getAllowOverrides());
      settingDTO.setValue(setting.getValue());
      settingDTO.setOrgIdentifier(setting.getOrgIdentifier());
      settingDTO.setProjectIdentifier(setting.getProjectIdentifier());
      return SettingResponseDTO.builder()
              .setting(settingDTO)
              .name(settingConfiguration.get().getName())
              .settingSource(SettingsServiceImpl.getSettingSource(setting.getOrgIdentifier(), setting.getProjectIdentifier()))
              .lastModifiedAt(setting.getLastModifiedAt())
              .build();
    } else {
      //ERROR
    }
    return null;
  }
}
