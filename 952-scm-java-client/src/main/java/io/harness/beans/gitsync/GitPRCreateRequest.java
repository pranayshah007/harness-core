/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans.gitsync;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.Trimmed;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

@Value
@Builder
@OwnedBy(DX)
public class GitPRCreateRequest {
  @Schema(description = "Source Branch for pull request") @NotEmpty @NotNull @Trimmed String sourceBranch;
  @Schema(description = "Target Branch for pull request") @NotEmpty @NotNull @Trimmed String targetBranch;
  @Schema(description = "PR title") @NotEmpty @NotNull String title;
  @Schema(description = "Git Sync Config Id") String yamlGitConfigRef; // removed @NotNull annotation
  @Schema(description = ACCOUNT_PARAM_MESSAGE) @NotBlank String accountIdentifier;
  @Schema(description = ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @Schema(
      description =
          "Specifies which token to use. If True, the SCM token will be used, else the Git Connector token will be used")
  boolean useUserFromToken;
}
