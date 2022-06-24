/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngsettings.SettingSource;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.PL)
@Data
@Builder
public class SettingResponseDTO {
  @NotNull SettingDTO setting;
  @NotNull @Schema(description = SettingConstants.NAME) String name;
  @Schema(description = SettingConstants.SOURCE) SettingSource settingSource;
  @Schema(description = SettingConstants.LAST_MODIFIED_AT) Long lastModifiedAt;
}
