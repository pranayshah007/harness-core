package io.harness.ngsettings.mapper;

import io.harness.exception.InvalidRequestException;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.ngsettings.entities.Setting;
import io.harness.ngsettings.entities.SettingConfiguration;
import io.harness.ngsettings.services.impl.SettingsServiceImpl;
import io.harness.repositories.SettingConfigurationRepository;

import com.google.inject.Inject;
import java.util.Optional;

public class SettingsMapper {
  @Inject SettingConfigurationRepository settingConfigurationRepository;

  public SettingDTO getSettingDTO(SettingConfiguration settingConfiguration, Setting setting) {
    return SettingDTO.builder()
        .identifier(settingConfiguration.getIdentifier())
        .orgIdentifier(setting.getOrgIdentifier())
        .projectIdentifier(setting.getProjectIdentifier())
        .allowedValues(settingConfiguration.getAllowedValues())
        .allowOverrides(setting.getAllowOverrides())
        .category(settingConfiguration.getCategory())
        .valueType(settingConfiguration.getValueType())
        .defaultValue(settingConfiguration.getDefaultValue())
        .value(setting.getValue())
        .build();
  }

  public SettingDTO getSettingDTO(
      SettingConfiguration settingConfiguration, String orgIdentifier, String projectIdentifier) {
    return SettingDTO.builder()
        .identifier(settingConfiguration.getIdentifier())
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
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
        .build();
  }

  public SettingResponseDTO settingtoSettingResponseDTO(Setting setting) {
    Optional<SettingConfiguration> settingConfiguration =
        settingConfigurationRepository.findByIdentifier(setting.getIdentifier());
    if (settingConfiguration.isPresent()) {
      SettingDTO settingDTO = getSettingDTO(settingConfiguration.get(), setting);
      return SettingResponseDTO.builder()
          .setting(settingDTO)
          .name(settingConfiguration.get().getName())
          .settingSource(
              SettingsServiceImpl.getSettingSource(setting.getOrgIdentifier(), setting.getProjectIdentifier()))
          .build();
    } else {
      throw new InvalidRequestException(
          String.format("Setting with identifier- [%s] does not exist", setting.getIdentifier()));
    }
  }

  public Setting settingConfigurationToSetting(SettingConfiguration settingConfiguration, String accountIdentifier,
      String orgIdentifier, String projectIdentifier) {
    return Setting.builder()
        .identifier(settingConfiguration.getIdentifier())
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .allowOverrides(settingConfiguration.getAllowOverrides())
        .category(settingConfiguration.getCategory())
        .value(settingConfiguration.getDefaultValue())
        .build();
  }
}
