/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.sdk;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitsync.interceptor.GitSyncConstants;
import io.harness.gitsync.persistance.GitSyncableEntity;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(DX)
public class EntityGitDetailsMapper {
  public EntityGitDetails mapEntityGitDetails(GitSyncableEntity gitSyncableEntity) {
    return EntityGitDetails.builder()
        .branch(gitSyncableEntity.getBranch())
        .objectId(gitSyncableEntity.getObjectIdOfYaml())
        .repoIdentifier(gitSyncableEntity.getYamlGitConfigRef())
        .rootFolder(gitSyncableEntity.getRootFolder())
        .filePath(gitSyncableEntity.getFilePath())
        .build();
  }

  public void copyEntityGitDetails(GitSyncableEntity fromGitSyncableEntity, GitSyncableEntity toGitSyncableEntity) {
    toGitSyncableEntity.setBranch(fromGitSyncableEntity.getBranch());
    toGitSyncableEntity.setIsFromDefaultBranch(fromGitSyncableEntity.getIsFromDefaultBranch());
    toGitSyncableEntity.setRootFolder(fromGitSyncableEntity.getRootFolder());
    toGitSyncableEntity.setYamlGitConfigRef(fromGitSyncableEntity.getYamlGitConfigRef());
    toGitSyncableEntity.setFilePath(fromGitSyncableEntity.getFilePath());
    toGitSyncableEntity.setObjectIdOfYaml(fromGitSyncableEntity.getObjectIdOfYaml());
  }

  public String nullIfDefault(String val) {
    if (EmptyPredicate.isEmpty(val) || val.equals(GitSyncConstants.DEFAULT)) {
      return null;
    }
    return val;
  }
}
