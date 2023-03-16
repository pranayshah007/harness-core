/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.gitsync.core.beans.GitCommit.GIT_COMMIT_PROCESSED_STATUS;

import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.beans.GitSyncDirection;
import io.harness.gitsync.common.helper.GitCommitMapper;
import io.harness.gitsync.core.beans.GitCommit;
import io.harness.gitsync.core.beans.GitCommit.GitCommitKeys;
import io.harness.gitsync.core.beans.GitCommit.GitCommitProcessingStatus;
import io.harness.gitsync.core.dtos.GitCommitDTO;
import io.harness.gitsync.core.service.GitCommitService;
import io.harness.gitsync.gitfileactivity.beans.GitFileProcessingSummary;
import io.harness.repositories.gitCommit.GitCommitRepository;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(DX)
public class GitCommitServiceImpl implements GitCommitService {
  private GitCommitRepository gitCommitRepository;
  private MongoTemplate mongoTemplate;

  @Override
  public GitCommitDTO save(GitCommitDTO gitCommitDTO) {
    GitCommit gitCommit = prepareGitCommit(gitCommitDTO);
    GitCommit gitCommitSaved = gitCommitRepository.save(gitCommit);
    return GitCommitMapper.toGitCommitDTO(gitCommitSaved);
  }

  @Override
  public Optional<GitCommitDTO> findByAccountIdAndCommitIdAndRepoAndBranchNameAndStatus(
      String accountId, String commitId, String repo, String branchName, List<GitCommitProcessingStatus> status) {
    Optional<GitCommit> gitCommit =
        gitCommitRepository.findByAccountIdentifierAndCommitIdAndRepoURLAndBranchNameAndStatusIn(
            accountId, commitId, repo, branchName, status);
    return gitCommit.map(GitCommitMapper::toGitCommitDTO);
  }

  @Override
  public Optional<GitCommitDTO> findGitCommitWithProcessedStatus(
      String accountId, String commitId, String repo, String branchName) {
    return findByAccountIdAndCommitIdAndRepoAndBranchNameAndStatus(
        accountId, commitId, repo, branchName, GIT_COMMIT_PROCESSED_STATUS);
  }

  @Override
  public Optional<GitCommitDTO> findLastProcessedGitCommit(String accountId, String repo, String branchName) {
    return findByAccountIdAndCommitIdAndRepoAndBranchName(accountId, repo, branchName, GIT_COMMIT_PROCESSED_STATUS);
  }

  @Override
  public Optional<GitCommitDTO> findByAccountIdAndCommitIdAndRepoAndBranchName(
      String accountId, String repo, String branchName, List<GitCommitProcessingStatus> status) {
    Optional<GitCommit> gitCommit =
        gitCommitRepository.findFirstByAccountIdentifierAndRepoURLAndBranchNameAndStatusInOrderByCreatedAtDesc(
            accountId, repo, branchName, status);
    return gitCommit.map(GitCommitMapper::toGitCommitDTO);
  }

  @Override
  public boolean isCommitAlreadyProcessed(String accountId, String headCommit, String repo, String branch) {
    final Optional<GitCommitDTO> gitCommitDTO = findGitCommitWithProcessedStatus(accountId, headCommit, repo, branch);
    if (gitCommitDTO.isPresent()) {
      log.info("Commit [id:{}] already processed [status:{}] on [date:{}]", gitCommitDTO.get().getCommitId(),
          gitCommitDTO.get().getStatus(), gitCommitDTO.get().getLastUpdatedAt());
      return true;
    }
    return false;
  }

  @Override
  public Optional<GitCommitDTO> findLastGitCommit(
      String accountIdentifier, String repo, String branchName, GitSyncDirection gitSyncDirection) {
    Optional<GitCommit> gitCommit =
        gitCommitRepository.findFirstByAccountIdentifierAndRepoURLAndBranchNameAndGitSyncDirectionOrderByCreatedAtDesc(
            accountIdentifier, repo, branchName, gitSyncDirection.name());
    return gitCommit.map(GitCommitMapper::toGitCommitDTO);
  }

  @Override
  public Optional<GitCommitDTO> findLastGitCommit(String accountIdentifier, String repo, String branchName) {
    Optional<GitCommit> gitCommit =
        gitCommitRepository.findFirstByAccountIdentifierAndRepoURLAndBranchNameOrderByCreatedAtDesc(
            accountIdentifier, repo, branchName);
    return gitCommit.map(GitCommitMapper::toGitCommitDTO);
  }

  @Override
  public UpdateResult upsertOnCommitIdAndRepoUrlAndGitSyncDirection(GitCommitDTO gitCommitDTO) {
    Criteria criteria = Criteria.where(GitCommitKeys.commitId)
                            .is(gitCommitDTO.getCommitId())
                            .and(GitCommitKeys.repoURL)
                            .is(gitCommitDTO.getRepoURL())
                            .and(GitCommitKeys.gitSyncDirection)
                            .is(gitCommitDTO.getGitSyncDirection());
    Update update = update(GitCommitKeys.status, gitCommitDTO.getStatus())
                        .set(GitCommitKeys.fileProcessingSummary, gitCommitDTO.getFileProcessingSummary());
    // TODO added explicitly createdAt for timebeing until annotation issue isn't resolved
    update.setOnInsert(GitCommitKeys.repoURL, gitCommitDTO.getRepoURL())
        .set(GitCommitKeys.commitId, gitCommitDTO.getCommitId())
        .set(GitCommitKeys.accountIdentifier, gitCommitDTO.getAccountIdentifier())
        .set(GitCommitKeys.branchName, gitCommitDTO.getBranchName())
        .set(GitCommitKeys.commitMessage, gitCommitDTO.getCommitMessage())
        .set(GitCommitKeys.gitSyncDirection, gitCommitDTO.getGitSyncDirection())
        .set(GitCommitKeys.fileProcessingSummary, gitCommitDTO.getFileProcessingSummary())
        .set(GitCommitKeys.failureReason, gitCommitDTO.getFailureReason())
        .set(GitCommitKeys.createdAt, System.currentTimeMillis());

    return mongoTemplate.upsert(new Query(criteria), update, GitCommit.class);
  }

  @Override
  public void deleteByAccount(String accountId) {
    gitCommitRepository.deleteAllByAccountIdentifier(accountId);
  }

  // -------------------------- PRIVATE METHODS -------------------------------

  private GitCommit prepareGitCommit(GitCommitDTO gitCommitDTO) {
    return GitCommit.builder()
        .accountIdentifier(gitCommitDTO.getAccountIdentifier())
        .branchName(gitCommitDTO.getBranchName())
        .commitId(gitCommitDTO.getCommitId())
        .commitMessage(gitCommitDTO.getCommitMessage())
        .failureReason(gitCommitDTO.getFailureReason())
        .fileProcessingSummary(gitCommitDTO.getFileProcessingSummary())
        .gitSyncDirection(gitCommitDTO.getGitSyncDirection())
        .repoURL(gitCommitDTO.getRepoURL())
        .status(gitCommitDTO.getStatus())
        .build();
  }

  private boolean isFileProcessingSummaryEmpty(GitFileProcessingSummary gitFileProcessingSummary) {
    if (isValueEmpty(gitFileProcessingSummary.getFailureCount())
        && isValueEmpty(gitFileProcessingSummary.getQueuedCount())
        && isValueEmpty(gitFileProcessingSummary.getSkippedCount())
        && isValueEmpty(gitFileProcessingSummary.getSuccessCount())
        && isValueEmpty(gitFileProcessingSummary.getTotalCount())) {
      return true;
    }

    return false;
  }

  private boolean isValueEmpty(Long value) {
    return value == null || value == 0;
  }
}
