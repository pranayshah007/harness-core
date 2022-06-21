/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.dtos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.GitRepoScopeParams;
import io.harness.gitsync.sdk.GitSyncApiConstants;

import io.swagger.v3.oas.annotations.Parameter;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(HarnessTeam.PL)
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class CreatePRRequest {
  String orgIdentifier;
  String projectIdentifier;
  @NotBlank String repoName;
  @NotBlank String sourceBranchName;
  @NotBlank String targetBranchName;
  @NotBlank String connectorRef;
  String title;
  @Parameter(description = GitSyncApiConstants.GIT_REPO_SCOPE_PARAM_MESSAGE) GitRepoScopeParams gitRepoScopeParams;
}
