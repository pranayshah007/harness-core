/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.remote;

import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.dto.SettingRequestDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.ngsettings.services.SettingsService;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class SettingsResourceImpl implements SettingsResource {
  SettingsService settingsService;
  FeatureFlagService featureFlagService;
  @Override
  public ResponseDTO<SettingValueResponseDTO> get(
      String identifier, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (!isSettingsFeatureEnabled(accountIdentifier)) {
      throw new InvalidRequestException(String.format("Feature not available for your account- %s", accountIdentifier));
    }
    return ResponseDTO.newResponse(
        settingsService.get(identifier, accountIdentifier, orgIdentifier, projectIdentifier));
  }

  @Override
  public ResponseDTO<List<SettingResponseDTO>> list(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SettingCategory category) {
    if (!isSettingsFeatureEnabled(accountIdentifier)) {
      throw new InvalidRequestException(String.format("Feature not available for your account- %s", accountIdentifier));
    }
    return ResponseDTO.newResponse(settingsService.list(accountIdentifier, orgIdentifier, projectIdentifier, category));
  }

  @Override
  public ResponseDTO<List<SettingResponseDTO>> update(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<SettingRequestDTO> settingRequestDTOList) {
    if (!isSettingsFeatureEnabled(accountIdentifier)) {
      throw new InvalidRequestException(String.format("Feature not available for your account- %s", accountIdentifier));
    }
    return ResponseDTO.newResponse(
        settingsService.update(accountIdentifier, orgIdentifier, projectIdentifier, settingRequestDTOList));
  }

  private boolean isSettingsFeatureEnabled(String accountIdentifier) {
    return featureFlagService.isEnabled(FeatureName.NG_SETTINGS, accountIdentifier);
  }
}
