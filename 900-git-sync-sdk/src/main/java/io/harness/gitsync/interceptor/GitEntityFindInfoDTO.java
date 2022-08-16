/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.interceptor;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.gitsync.sdk.GitSyncApiConstants;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Getter
@Builder
@FieldNameConstants(innerTypeName = "GitEntityFindInfoKeys")
@Schema(name = "GitEntityFindInfo",
    description = "Details to find Git Entity including: Git Sync Config Id and Branch Name")
@OwnedBy(DX)
@NoArgsConstructor
@AllArgsConstructor
@TargetModule(HarnessModule._878_NG_COMMON_UTILITIES)
public class GitEntityFindInfoDTO {
  @Parameter(description = GitSyncApiConstants.BRANCH_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.BRANCH_KEY)
  String branch;
  @Parameter(description = GitSyncApiConstants.REPOID_PARAM_MESSAGE)
  @QueryParam(GitSyncApiConstants.REPO_IDENTIFIER_KEY)
  String yamlGitConfigId;
  @Parameter(description = "if true, return all the default entities")
  @QueryParam(GitSyncApiConstants.DEFAULT_FROM_OTHER_REPO)
  Boolean defaultFromOtherRepo;
  @Hidden
  @Parameter(description = "Repo URL of parent entity if its remote")
  @QueryParam(GitSyncApiConstants.PARENT_ENTITY_REPO_URL)
  String parentEntityRepoURL;
  @Hidden
  @Parameter(description = "Connector ref of parent entity if its remote")
  @QueryParam(GitSyncApiConstants.PARENT_ENTITY_CONNECTOR_REF)
  String parentEntityConnectorRef;
  @Hidden
  @Parameter(description = "Repo name of parent entity if its remote")
  @QueryParam(GitSyncApiConstants.PARENT_ENTITY_REPO_NAME)
  String parentEntityRepoName;
}