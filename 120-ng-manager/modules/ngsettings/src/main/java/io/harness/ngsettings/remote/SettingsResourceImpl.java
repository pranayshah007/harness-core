package io.harness.ngsettings.remote;

import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.setting.SettingCategory;
import io.harness.ngsettings.api.SettingsService;
import io.harness.ngsettings.beans.SettingRequestDTO;
import io.harness.ngsettings.beans.SettingResponseDTO;
import io.harness.ngsettings.beans.SettingValueRequestDTO;
import io.harness.ngsettings.beans.SettingValueResponseDTO;

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
