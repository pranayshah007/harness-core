/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.beans;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingValueType;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(HarnessTeam.PL)
@Data
@Builder
public class SettingDTO {
  @Schema(description = SettingConstants.IDENTIFIER) @NotNull @NotBlank @EntityIdentifier String identifier;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @NotNull @NotBlank @Schema(description = SettingConstants.CATEGORY) SettingCategory category;
  @NotNull @NotBlank @Schema(description = SettingConstants.VALUE_TYPE) SettingValueType valueType;
  @Schema(description = SettingConstants.ALLOWED_VALUES) Set<String> allowedValues;
  @NotNull @NotBlank @Schema(description = SettingConstants.ALLOW_OVERRIDES) Boolean allowOverrides;
  @Schema(description = SettingConstants.VALUE) String value;
  @Schema(description = SettingConstants.DEFAULT_VALUE) String defaultValue;
}
