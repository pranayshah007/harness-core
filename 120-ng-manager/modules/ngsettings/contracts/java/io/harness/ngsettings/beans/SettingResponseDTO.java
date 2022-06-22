package io.harness.ngsettings.beans;

import io.harness.ng.core.setting.SettingSource;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;

public class SettingResponseDTO {
  @NotNull SettingDTO setting;
  @NotNull @Schema(description = SettingConstants.NAME) String name;
  @Schema(description = SettingConstants.SOURCE)
  SettingSource settingSource;
  @Schema(description = SettingConstants.LAST_MODIFIED_AT) Long lastModifiedAt;
}
