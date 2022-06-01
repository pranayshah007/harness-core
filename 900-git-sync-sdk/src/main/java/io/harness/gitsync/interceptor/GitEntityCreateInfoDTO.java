/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.interceptor;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.StoreType;
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
@FieldNameConstants(innerTypeName = "GitEntityCreateInfoKeys")
@OwnedBy(DX)
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "GitEntityCreateInfo", description = "This contains details of the Git Entity for creation")
public class GitEntityCreateInfoDTO {
  @Parameter(description = GitSyncApiConstants.BRANCH_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.BRANCH_KEY)
  String branch;
  @Parameter(description = GitSyncApiConstants.REPOID_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.REPO_IDENTIFIER_KEY)
  String yamlGitConfigId;
  @Parameter(description = GitSyncApiConstants.FOLDER_PATH_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.FOLDER_PATH)
  String folderPath;
  @Parameter(description = GitSyncApiConstants.FILEPATH_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.FILE_PATH_KEY)
  String filePath;
  @Parameter(description = GitSyncApiConstants.FILEPATH_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.COMMIT_MSG_KEY)
  String commitMsg;
  @Parameter(description = "Checks the new branch")
  @DefaultValue("false")
  @QueryParam(GitSyncApiConstants.NEW_BRANCH)
  Boolean isNewBranch;
  @Parameter(description = GitSyncApiConstants.DEFAULT_BRANCH_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.BASE_BRANCH)
  String baseBranch;

  // query parameters for simplified git experience
  @Parameter(description = GitSyncApiConstants.GIT_CONNECTOR_REF_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.CONNECTOR_REF)
  String connectorRef;
  @Parameter(description = GitSyncApiConstants.STORE_TYPE_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.STORE_TYPE)
  StoreType storeType;
  @Parameter(description = GitSyncApiConstants.REPO_NAME_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.REPO_NAME)
  String repoName;
}
