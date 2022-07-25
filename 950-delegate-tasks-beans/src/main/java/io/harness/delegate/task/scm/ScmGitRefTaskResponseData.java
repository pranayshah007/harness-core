/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.scm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.DX)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ScmGitRefTaskResponseData implements DelegateResponseData {
  GitRefType gitRefType;
  String branch;
  String repoUrl;
  byte[] listBranchesResponse;
  byte[] listCommitsResponse;
  byte[] listCommitsInPRResponse;
  byte[] compareCommitsResponse;
  byte[] findPRResponse;
  byte[] getLatestCommitResponse;
  byte[] findCommitResponse;
  byte[] createBranchResponse;
  byte[] getUserReposResponse;
  byte[] getListBranchesWithDefaultResponse;
  byte[] getUserRepoResponse;
  byte[] getLatestCommitOnFileResponse;
}
