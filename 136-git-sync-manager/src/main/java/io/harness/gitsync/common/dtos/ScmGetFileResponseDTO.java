/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.dtos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.beans.ScmCacheDetails;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@OwnedBy(HarnessTeam.PL)
@Getter
@FieldDefaults(level = AccessLevel.PROTECTED)
@SuperBuilder
public class ScmGetFileResponseDTO {
  String fileContent;
  String commitId;
  String blobId;
  String branchName;
  ScmCacheDetails cacheDetails;
  boolean isGitDefaultBranch;

  public ScmGetFileResponseV2DTO toScmGetFileResponseV2DTO() {
    return ScmGetFileResponseV2DTO.builder()
        .fileContent(fileContent)
        .commitId(commitId)
        .cacheDetails(cacheDetails)
        .branchName(branchName)
        .blobId(blobId)
        .isGitDefaultBranch(isGitDefaultBranch)
        .build();
  }
}
