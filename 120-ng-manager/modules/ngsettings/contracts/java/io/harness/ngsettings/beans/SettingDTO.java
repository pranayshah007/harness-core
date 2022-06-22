package io.harness.ngsettings.beans;

import io.harness.NGCommonEntityConstants;
import io.harness.data.validator.EntityIdentifier;
import io.harness.ng.core.setting.SettingCategory;
import io.harness.ng.core.setting.SettingValueType;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotBlank;

public class SettingDTO {
  @Schema(description = SettingConstants.IDENTIFIER) @NotNull @NotBlank @EntityIdentifier String identifier;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @NotNull @NotBlank @Schema(description = SettingConstants.CATEGORY) SettingCategory category;
  @NotNull @NotBlank @Schema(description = SettingConstants.VALUE_TYPE) SettingValueType valueType;
  @Schema(description = SettingConstants.ALLOWED_VALUES) Set<String> allowedValues;
  @NotNull @NotBlank @Schema(description = SettingConstants.ALLOW_OVERRIDES) Boolean allowOverrides;
  @Schema(description = SettingConstants.VALUE) String value;
}
