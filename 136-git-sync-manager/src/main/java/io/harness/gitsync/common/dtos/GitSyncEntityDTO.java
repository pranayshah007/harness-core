/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.dtos;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.EntityType;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.gitsync.common.helper.RepoProviderHelper;
import io.harness.gitsync.common.utils.GitSyncFilePathUtils;
import io.harness.gitsync.sdk.GitSyncApiConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "GitSyncEntityDTOKeys")
@Schema(name = "GitSyncEntity", description = "This contains details of the Git Sync Entity")
@OwnedBy(DX)
public class GitSyncEntityDTO {
  @Schema(description = "Name of the Entity") private String entityName;
  @Schema(description = GitSyncApiConstants.ENTITY_TYPE_PARAM_MESSAGE) private EntityType entityType;
  @Schema(description = "Id of the Entity") private String entityIdentifier;
  @Schema(description = "Id of the Connector referenced in Git") private String gitConnectorId;
  @Schema(description = GitSyncApiConstants.REPO_URL_PARAM_MESSAGE) @JsonProperty("repoUrl") private String repo;
  @Schema(description = GitSyncApiConstants.BRANCH_PARAM_MESSAGE) private String branch;
  @Schema(description = GitSyncApiConstants.FOLDER_PATH_PARAM_MESSAGE) private String folderPath;
  @Schema(description = GitSyncApiConstants.FILEPATH_PARAM_MESSAGE) private String entityGitPath;
  @Schema(description = "This contains details about the Entity’s Scope and its Identifier")
  EntityReference entityReference;
  @Schema(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @JsonIgnore String accountId;
  @Schema(hidden = true) @JsonIgnore String lastCommitId;
  @Schema(description = GitSyncApiConstants.ENTITY_GIT_URL_DESCRIPTION) String entityUrl;
  @JsonIgnore RepoProviders repoProvider;

  public String getEntityUrl() {
    String completeFilePath = GitSyncFilePathUtils.createFilePath(folderPath, entityGitPath);
    /*
     * This is a fallback logic which will be used if the migration to update repo provider in
     *  the entity fails.
     *
     *  todo @deepak Remove this if condition after the migration has made it to the prod
     */
    if (repoProvider == null) {
      log.info("The repo provider was null for the git entity with the identifier {}", entityIdentifier);
      repoProvider = RepoProviderHelper.getRepoProviderFromTheUrl(repo);
    }

    return RepoProviderHelper.getTheFilePathUrl(repo, branch, repoProvider, completeFilePath);
  }
}
