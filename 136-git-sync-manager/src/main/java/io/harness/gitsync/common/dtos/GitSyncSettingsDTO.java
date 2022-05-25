/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.dtos;

import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "GitSyncSettings", description = "This contains details of Git Sync Settings")
@OwnedBy(DX)
public class GitSyncSettingsDTO {
  @Schema(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE, hidden = true) String accountIdentifier;
  @Schema(description = PROJECT_PARAM_MESSAGE) @NotNull String projectIdentifier;
  @Schema(description = ORG_PARAM_MESSAGE) @NotNull String orgIdentifier;
  @Schema(
      description =
          "Specifies Connectivity Mode for Git Sync. If True, executes through Delegate, else executes through Platform. The default value is True")
  @NotNull
  boolean executeOnDelegate;
}
