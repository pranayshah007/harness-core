/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.gitCommit;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.core.beans.GitCommit;
import io.harness.gitsync.core.beans.GitCommit.GitCommitProcessingStatus;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.DX)
public interface GitCommitRepository extends CrudRepository<GitCommit, String> {
  Optional<GitCommit> findFirstByAccountIdentifierAndRepoURLAndBranchNameAndStatusInOrderByCreatedAtDesc(
      String accountIdentifier, String repoURL, String branchName, List<GitCommitProcessingStatus> status);

  Optional<GitCommit> findByAccountIdentifierAndCommitIdAndRepoURLAndBranchNameAndStatusIn(String accountIdentifier,
      String commitId, String repoURL, String branchName, List<GitCommitProcessingStatus> status);

  Optional<GitCommit> findFirstByAccountIdentifierAndRepoURLAndBranchNameOrderByCreatedAtDesc(
      String accountIdentifier, String repoURL, String branchName);

  Optional<GitCommit> findFirstByAccountIdentifierAndRepoURLAndBranchNameAndGitSyncDirectionOrderByCreatedAtDesc(
      String accountIdentifier, String repoURL, String branchName, String gitSyncDirection);

  List<GitCommit> findAllByAccountIdentifier(String accountIdentifier);

  long deleteAllByAccountIdentifier(String accountIdentifier);
}
