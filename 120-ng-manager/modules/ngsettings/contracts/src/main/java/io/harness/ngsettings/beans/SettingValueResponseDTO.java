package io.harness.ngsettings.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.setting.SettingValueType;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;

import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(HarnessTeam.PL)
@Data
@Builder
public class SettingValueResponseDTO {
  @NotNull @NotBlank @Schema(description = SettingConstants.VALUE_TYPE) SettingValueType valueType;
  @NotNull @NotBlank @Schema(description = SettingConstants.VALUE) String value;
}
