/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.remote;

import static io.harness.ngsettings.SettingPermissions.SETTING_EDIT_PERMISSION;
import static io.harness.ngsettings.SettingPermissions.SETTING_RESOURCE_TYPE;
import static io.harness.ngsettings.SettingPermissions.SETTING_VIEW_PERMISSION;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.dto.SettingRequestDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.ngsettings.dto.SettingUpdateResponseDTO;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.ngsettings.services.SettingsService;
import io.harness.ngsettings.utils.FeatureFlagHelper;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class SettingsResourceImpl implements SettingsResource {
  public static final String FEATURE_NOT_AVAILABLE = "Feature not available for your account- %s";
  SettingsService settingsService;
  FeatureFlagHelper featureFlagHelper;
  private final AccessControlClient accessControlClient;
  @Override
  @NGAccessControlCheck(resourceType = SETTING_RESOURCE_TYPE, permission = SETTING_VIEW_PERMISSION)
  public ResponseDTO<SettingValueResponseDTO> get(String identifier, @AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier) {
    if (!isSettingsFeatureEnabled(accountIdentifier)) {
      throw new InvalidRequestException(String.format(FEATURE_NOT_AVAILABLE, accountIdentifier));
    }
    return ResponseDTO.newResponse(
        settingsService.get(identifier, accountIdentifier, orgIdentifier, projectIdentifier));
  }

  @Override
  @NGAccessControlCheck(resourceType = SETTING_RESOURCE_TYPE, permission = SETTING_VIEW_PERMISSION)
  public ResponseDTO<List<SettingResponseDTO>> list(@AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier, SettingCategory category,
      String groupIdentifier) {
    if (!isSettingsFeatureEnabled(accountIdentifier)) {
      throw new InvalidRequestException(String.format(FEATURE_NOT_AVAILABLE, accountIdentifier));
    }
    return ResponseDTO.newResponse(
        settingsService.list(accountIdentifier, orgIdentifier, projectIdentifier, category, groupIdentifier));
  }

  @Override
  @NGAccessControlCheck(resourceType = SETTING_RESOURCE_TYPE, permission = SETTING_EDIT_PERMISSION)
  public ResponseDTO<List<SettingUpdateResponseDTO>> update(@AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier,
      List<SettingRequestDTO> settingRequestDTOList) {
    if (!isSettingsFeatureEnabled(accountIdentifier)) {
      throw new InvalidRequestException(String.format(FEATURE_NOT_AVAILABLE, accountIdentifier));
    }
    checkFeatureFlagToUpdateJWTTokenSettingEnabled(accountIdentifier, settingRequestDTOList);
    return ResponseDTO.newResponse(
        settingsService.update(accountIdentifier, orgIdentifier, projectIdentifier, settingRequestDTOList));
  }

  private void checkFeatureFlagToUpdateJWTTokenSettingEnabled(
      String accountIdentifier, List<SettingRequestDTO> settingRequestDTOList) {
    boolean jwtSettingVariable = false;
    for (SettingRequestDTO requestDto : settingRequestDTOList) {
      if (requestDto != null
          && (SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_KEY_IDENTIFIER.equals(requestDto.getIdentifier())
              || SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_VALUE_IDENTIFIER.equals(requestDto.getIdentifier())
              || SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_PUBLIC_KEY_IDENTIFIER.equals(
                  requestDto.getIdentifier())
              || SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_SERVICE_PRINCIPAL_IDENTIFIER.equals(
                  requestDto.getIdentifier()))) {
        jwtSettingVariable = true;
        break;
      }
    }
    if (jwtSettingVariable && !isJwtTokenSettingsFeatureEnabled(accountIdentifier)) {
      throw new InvalidRequestException(String.format("NG_SCIM_JWT: Feature [%s] is not enabled for your account - %s",
          FeatureName.PL_ENABLE_JWT_TOKEN_ACCOUNT_SETTINGS.name(), accountIdentifier));
    }
  }

  private boolean isSettingsFeatureEnabled(String accountIdentifier) {
    return featureFlagHelper.isEnabled(accountIdentifier, FeatureName.NG_SETTINGS);
  }

  private boolean isJwtTokenSettingsFeatureEnabled(String accountIdentifier) {
    return featureFlagHelper.isEnabled(accountIdentifier, FeatureName.PL_ENABLE_JWT_TOKEN_ACCOUNT_SETTINGS);
  }
}
