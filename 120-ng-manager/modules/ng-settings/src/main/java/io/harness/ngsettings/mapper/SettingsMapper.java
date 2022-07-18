/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.mapper;

import io.harness.ngsettings.SettingSource;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingRequestDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.ngsettings.dto.SettingUpdateResponseDTO;
import io.harness.ngsettings.entities.Setting;
import io.harness.ngsettings.entities.SettingConfiguration;
import io.harness.ngsettings.utils.SettingUtils;

public class SettingsMapper {
  public SettingDTO writeSettingDTO(Setting setting, SettingConfiguration settingConfiguration) {
    return SettingDTO.builder()
        .identifier(setting.getIdentifier())
        .name(settingConfiguration.getName())
        .orgIdentifier(setting.getOrgIdentifier())
        .projectIdentifier(setting.getProjectIdentifier())
        .allowedValues(settingConfiguration.getAllowedValues())
        .allowOverrides(setting.getAllowOverrides())
        .category(settingConfiguration.getCategory())
        .group(settingConfiguration.getGroup())
        .valueType(settingConfiguration.getValueType())
        .defaultValue(settingConfiguration.getDefaultValue())
        .value(setting.getValue())
        .settingSource(SettingUtils.getSettingSource(setting))
        .build();
  }

  public SettingDTO writeSettingDTO(SettingConfiguration settingConfiguration) {
    return SettingDTO.builder()
        .identifier(settingConfiguration.getIdentifier())
        .name(settingConfiguration.getName())
        .category(settingConfiguration.getCategory())
        .group(settingConfiguration.getGroup())
        .valueType(settingConfiguration.getValueType())
        .defaultValue(settingConfiguration.getDefaultValue())
        .value(settingConfiguration.getDefaultValue())
        .allowedValues(settingConfiguration.getAllowedValues())
        .allowOverrides(true)
        .settingSource(SettingSource.DEFAULT)
        .build();
  }

  public SettingResponseDTO writeSettingResponseDTO(Setting setting, SettingConfiguration settingConfiguration) {
    return SettingResponseDTO.builder()
        .setting(writeSettingDTO(setting, settingConfiguration))
        .lastModifiedAt(setting.getLastModifiedAt())
        .build();
  }

  public SettingResponseDTO writeSettingResponseDTO(SettingConfiguration settingConfiguration) {
    return SettingResponseDTO.builder().setting(writeSettingDTO(settingConfiguration)).build();
  }

  public SettingDTO writeNewDTO(
      Setting setting, SettingRequestDTO settingRequestDTO, SettingConfiguration settingConfiguration) {
    return SettingDTO.builder()
        .identifier(setting.getIdentifier())
        .name(settingConfiguration.getName())
        .orgIdentifier(setting.getOrgIdentifier())
        .projectIdentifier(setting.getProjectIdentifier())
        .allowedValues(settingConfiguration.getAllowedValues())
        .allowOverrides(settingRequestDTO.getAllowOverrides())
        .category(settingConfiguration.getCategory())
        .group(settingConfiguration.getGroup())
        .value(settingRequestDTO.getValue())
        .valueType(settingConfiguration.getValueType())
        .defaultValue(settingConfiguration.getDefaultValue())
        .build();
  }

  public SettingDTO writeNewDTO(String orgIdentifier, String projectIdentifier, SettingRequestDTO settingRequestDTO,
      SettingConfiguration settingConfiguration) {
    return SettingDTO.builder()
        .identifier(settingConfiguration.getIdentifier())
        .name(settingConfiguration.getName())
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .allowedValues(settingConfiguration.getAllowedValues())
        .allowOverrides(settingRequestDTO.getAllowOverrides())
        .category(settingConfiguration.getCategory())
        .group(settingConfiguration.getGroup())
        .value(settingRequestDTO.getValue())
        .valueType(settingConfiguration.getValueType())
        .defaultValue(settingConfiguration.getDefaultValue())
        .build();
  }

  public SettingUpdateResponseDTO writeBatchResponseDTO(SettingResponseDTO responseDTO) {
    return SettingUpdateResponseDTO.builder()
        .updateStatus(true)
        .identifier(responseDTO.getSetting().getIdentifier())
        .setting(responseDTO.getSetting())
        .lastModifiedAt(responseDTO.getLastModifiedAt())
        .build();
  }

  public SettingUpdateResponseDTO writeBatchResponseDTO(String identifier, Exception exception) {
    return SettingUpdateResponseDTO.builder()
        .updateStatus(false)
        .identifier(identifier)
        .errorMessage(exception.getMessage())
        .build();
  }

  public Setting toSetting(String accountIdentifier, SettingDTO settingDTO) {
    return Setting.builder()
        .identifier(settingDTO.getIdentifier())
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(settingDTO.getOrgIdentifier())
        .projectIdentifier(settingDTO.getProjectIdentifier())
        .category(settingDTO.getCategory())
        .group(settingDTO.getGroup())
        .allowOverrides(settingDTO.getAllowOverrides())
        .value(settingDTO.getValue())
        .valueType(settingDTO.getValueType())
        .build();
  }
}
