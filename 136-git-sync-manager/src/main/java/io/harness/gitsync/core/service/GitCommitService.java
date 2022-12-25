/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.service;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.beans.GitSyncDirection;
import io.harness.gitsync.core.beans.GitCommit.GitCommitProcessingStatus;
import io.harness.gitsync.core.dtos.GitCommitDTO;

import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Optional;

@OwnedBy(DX)
public interface GitCommitService {
  GitCommitDTO save(GitCommitDTO gitCommit);

  Optional<GitCommitDTO> findByAccountIdAndCommitIdAndRepoAndBranchNameAndStatus(
      String accountId, String commitId, String repo, String branchName, List<GitCommitProcessingStatus> status);

  Optional<GitCommitDTO> findGitCommitWithProcessedStatus(
      String accountId, String commitId, String repo, String branchName);

  Optional<GitCommitDTO> findLastProcessedGitCommit(String accountId, String repo, String branchName);

  Optional<GitCommitDTO> findByAccountIdAndCommitIdAndRepoAndBranchName(
      String accountId, String repo, String branchName, List<GitCommitProcessingStatus> status);

  boolean isCommitAlreadyProcessed(String accountId, String headCommit, String repo, String branch);

  Optional<GitCommitDTO> findLastGitCommit(
      String accountIdentifier, String repo, String branchName, GitSyncDirection gitSyncDirection);

  Optional<GitCommitDTO> findLastGitCommit(String accountIdentifier, String repo, String branchName);

  UpdateResult upsertOnCommitIdAndRepoUrlAndGitSyncDirection(GitCommitDTO gitCommitDTO);

  void deleteByAccount(String accountId);
}
