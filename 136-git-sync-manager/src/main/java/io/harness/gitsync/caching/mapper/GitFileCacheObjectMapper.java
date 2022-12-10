/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.caching.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.caching.beans.GitFileCacheObject;
import io.harness.gitsync.caching.entity.GitFileObject;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class GitFileCacheObjectMapper {
  public GitFileCacheObject fromEntity(GitFileObject gitFileObject) {
    return GitFileCacheObject.builder()
        .fileContent(gitFileObject.getFileContent())
        .objectId(gitFileObject.getObjectId())
        .commitId(gitFileObject.getCommitId())
        .build();
  }

  public GitFileObject toEntity(GitFileCacheObject gitFileCacheObject) {
    return GitFileObject.builder()
        .fileContent(gitFileCacheObject.getFileContent())
        .objectId(gitFileCacheObject.getObjectId())
        .commitId(gitFileCacheObject.getCommitId())
        .build();
  }
}
