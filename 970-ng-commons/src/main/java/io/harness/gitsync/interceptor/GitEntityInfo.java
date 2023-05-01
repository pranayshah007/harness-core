/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitsync.interceptor;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;

@Getter
@Builder(toBuilder = true)
@FieldNameConstants(innerTypeName = "GitEntityInfoKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(DX)
public class GitEntityInfo {
  @Setter String branch;
  String yamlGitConfigId;
  @Wither @Setter String folderPath;
  @Wither @Setter String filePath;
  @Wither String commitMsg;
  @Wither @Setter String lastObjectId; // required in case of update file
  boolean isNewBranch;
  boolean isSyncFromGit;
  @Wither boolean findDefaultFromOtherRepos;
  String baseBranch;
  String commitId; // used for passing commitId in case of g2h.
  Boolean isFullSyncFlow;
  String resolvedConflictCommitId;
  @Setter StoreType storeType;
  @Setter String connectorRef;
  @Setter String repoName;
  @Wither @Setter String lastCommitId;
  @Setter String parentEntityRepoUrl;
  @Setter String parentEntityConnectorRef; // connector ref of entity under whose context actions are occurring
  @Setter String parentEntityRepoName; // repo name of entity under whose context actions are occurring
  @Setter String parentEntityAccountIdentifier;
  @Setter String parentEntityOrgIdentifier;
  @Setter String parentEntityProjectIdentifier;
  @Setter Boolean isDefaultBranch;

  public boolean isNull() {
    // todo @Abhinav Maybe we should use null in place of default
    final String DEFAULT = "__default__";
    boolean isRepoNull = isEmpty(yamlGitConfigId) || yamlGitConfigId.equals(DEFAULT);
    boolean isBranchNull = isEmpty(branch) || branch.equals(DEFAULT);
    return isRepoNull && isBranchNull;
  }

  public EntityGitDetails toEntityGitDetails() {
    if (isNull()) {
      return null;
    }
    return EntityGitDetails.builder()
        .branch(branch)
        .repoIdentifier(yamlGitConfigId)
        .rootFolder(folderPath)
        .filePath(filePath)
        .repoName(repoName)
        .parentEntityConnectorRef(parentEntityConnectorRef)
        .parentEntityRepoName(parentEntityRepoName)
        .build();
  }
}
