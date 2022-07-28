/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.interceptor;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.sdk.GitSyncApiConstants;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Getter
@Builder
@FieldNameConstants(innerTypeName = "GitImportInfoDTOKeys")
@Schema(name = "GitImportInfoDTO", description = "Details to importing Git Entity")
@OwnedBy(DX)
@NoArgsConstructor
@AllArgsConstructor
public class GitImportInfoDTO {
  @Parameter(description = GitSyncApiConstants.GIT_CONNECTOR_REF_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.CONNECTOR_REF)
  String connectorRef;
  @Parameter(description = GitSyncApiConstants.REPO_NAME_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.REPO_NAME)
  String repoName;
  @Parameter(description = GitSyncApiConstants.BRANCH_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.BRANCH_KEY)
  String branch;
  @Parameter(description = GitSyncApiConstants.FILEPATH_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.FILE_PATH_KEY)
  String filePath;
  @Parameter(description = GitSyncApiConstants.FORCE_IMPORT)
  @QueryParam(GitSyncApiConstants.FORCE_IMPORT)
  @DefaultValue("false")
  Boolean isForceImport;
}
