/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.dto;

import static io.harness.ngsettings.SettingConstants.ALLOW_OVERRIDES;
import static io.harness.ngsettings.SettingConstants.IDENTIFIER;
import static io.harness.ngsettings.SettingConstants.UPDATE_TYPE;
import static io.harness.ngsettings.SettingConstants.VALUE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.ngsettings.SettingUpdateType;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.PL)
@Data
@Builder
public class SettingRequestDTO {
  @Schema(description = IDENTIFIER) @NotBlank @EntityIdentifier String identifier;
  @Schema(description = VALUE) String value;
  @NotNull @NotBlank @Schema(description = ALLOW_OVERRIDES) Boolean allowOverrides;
  @NotNull @NotBlank @Schema(description = UPDATE_TYPE) SettingUpdateType updateType;
}
