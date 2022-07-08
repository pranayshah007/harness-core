/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ngsettings.SettingConstants.BATCH_ITEM_ERROR_MESSAGE;
import static io.harness.ngsettings.SettingConstants.BATCH_ITEM_RESPONSE_STATUS;
import static io.harness.ngsettings.SettingConstants.IDENTIFIER;
import static io.harness.ngsettings.SettingConstants.LAST_MODIFIED_AT;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(PL)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SettingUpdateResponseDTO {
  @Schema(description = IDENTIFIER) @NotBlank String identifier;
  @NotNull SettingDTO setting;
  @Schema(description = LAST_MODIFIED_AT) Long lastModifiedAt;
  @Schema(description = BATCH_ITEM_RESPONSE_STATUS) boolean updateStatus;
  @Schema(description = BATCH_ITEM_ERROR_MESSAGE) String errorMessage;
}
