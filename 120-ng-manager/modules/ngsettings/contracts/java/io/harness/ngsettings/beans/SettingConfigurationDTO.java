package io.harness.ngsettings.beans;

import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.ng.core.setting.SettingCategory;
import io.harness.ng.core.setting.SettingValueType;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotBlank;

public class SettingConfigurationDTO {
  @Schema(description = SettingConstants.IDENTIFIER) @NotNull @NotBlank @EntityIdentifier String identifier;
  @Schema(description = SettingConstants.NAME) @NotNull @NotBlank @NGEntityName String name;
  @Schema(description = SettingConstants.CATEGORY) @NotNull @NotBlank SettingCategory category;
  @Schema(description = SettingConstants.DEFAULT_VALUE) @NotNull @NotBlank String defaultValue;
  @Schema(description = SettingConstants.VALUE_TYPE) @NotNull @NotBlank SettingValueType valueType;
  @Schema(description = SettingConstants.ALLOWED_VALUES) Set<String> allowedValues;
  @Schema(description = SettingConstants.ALLOW_OVERRIDES) Boolean allowOverrides;
  @Schema(description = SettingConstants.ALLOWED_SCOPES) Set<String> allowedScopes;
}
