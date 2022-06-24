/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.services;

import io.harness.ngsettings.SettingCategory;
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
