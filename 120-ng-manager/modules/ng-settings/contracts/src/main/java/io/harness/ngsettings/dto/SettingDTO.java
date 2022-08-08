/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.dto;

import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.ngsettings.SettingConstants.ALLOWED_VALUES;
import static io.harness.ngsettings.SettingConstants.ALLOW_EDIT;
import static io.harness.ngsettings.SettingConstants.ALLOW_OVERRIDES;
import static io.harness.ngsettings.SettingConstants.CATEGORY;
import static io.harness.ngsettings.SettingConstants.DEFAULT_VALUE;
import static io.harness.ngsettings.SettingConstants.GROUP_ID;
import static io.harness.ngsettings.SettingConstants.IDENTIFIER;
import static io.harness.ngsettings.SettingConstants.NAME;
import static io.harness.ngsettings.SettingConstants.SOURCE;
import static io.harness.ngsettings.SettingConstants.VALUE;
import static io.harness.ngsettings.SettingConstants.VALUE_TYPE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingSource;
import io.harness.ngsettings.SettingValueType;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.PL)
@Data
@Builder
public class SettingDTO implements YamlDTO {
  @Schema(description = IDENTIFIER) @NotNull @NotBlank @EntityIdentifier String identifier;
  @Schema(description = NAME) @NotNull @NotBlank @EntityIdentifier String name;
  @Schema(description = ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @NotNull @NotBlank @Schema(description = CATEGORY) SettingCategory category;
  @NotNull @NotBlank @Schema(description = GROUP_ID) String groupIdentifier;
  @NotNull @NotBlank @Schema(description = VALUE_TYPE) SettingValueType valueType;
  @Schema(description = ALLOWED_VALUES) Set<String> allowedValues;
  @NotNull @NotBlank @Schema(description = ALLOW_OVERRIDES) Boolean allowOverrides;
  @Schema(description = VALUE) String value;
  @Schema(description = DEFAULT_VALUE) String defaultValue;
  @Schema(description = SOURCE) SettingSource settingSource;
  @NotNull @NotEmpty @Schema(description = ALLOW_EDIT) Boolean isSettingEditable;
}
