package io.harness.ngsettings.beans;

import io.harness.ng.core.setting.SettingValueType;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotBlank;

public class SettingValueResponseDTO {
  @NotNull @NotBlank @Schema(description = SettingConstants.VALUE_TYPE) SettingValueType valueType;
  @NotNull @NotBlank @Schema(description = SettingConstants.VALUE) String value;
}
