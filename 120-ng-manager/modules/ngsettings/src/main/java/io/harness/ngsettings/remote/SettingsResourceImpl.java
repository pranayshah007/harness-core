/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.remote;

import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.beans.SettingRequestDTO;
import io.harness.ngsettings.beans.SettingResponseDTO;
import io.harness.ngsettings.beans.SettingValueRequestDTO;
import io.harness.ngsettings.beans.SettingValueResponseDTO;
import io.harness.ngsettings.services.SettingsService;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class SettingsResourceImpl implements SettingsResource {
  SettingsService settingsService;
  @Override
  public ResponseDTO<SettingValueResponseDTO> get(String identifier, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, SettingValueRequestDTO settingValueRequetDTO) {
    return ResponseDTO.newResponse(
        settingsService.get(accountIdentifier, orgIdentifier, projectIdentifier, settingValueRequetDTO));
  }

  @Override
  public ResponseDTO<List<SettingResponseDTO>> list(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SettingCategory category) {
    return ResponseDTO.newResponse(settingsService.list(accountIdentifier, orgIdentifier, projectIdentifier, category));
  }

  @Override
  public ResponseDTO<List<SettingResponseDTO>> update(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<SettingRequestDTO> settingRequestDTOList) {
    return ResponseDTO.newResponse(
        settingsService.update(accountIdentifier, orgIdentifier, projectIdentifier, settingRequestDTOList));
  }
}
