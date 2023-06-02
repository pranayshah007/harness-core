/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.gitsync.common.beans.GitToHarnessProcessingStepStatus.DONE;
import static io.harness.gitsync.common.beans.GitToHarnessProcessingStepType.GET_FILES;
import static io.harness.gitsync.common.beans.YamlChangeSetEventType.BRANCH_SYNC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.beans.GitToHarnessFileProcessingRequest;
import io.harness.gitsync.common.beans.GitToHarnessProcessingResponse;
import io.harness.gitsync.common.beans.GitToHarnessProcessingStepStatus;
import io.harness.gitsync.common.beans.GitToHarnessProcessingStepType;
import io.harness.gitsync.common.beans.GitToHarnessProgress;
import io.harness.gitsync.common.beans.GitToHarnessProgress.GitToHarnessProgressKeys;
import io.harness.gitsync.common.beans.GitToHarnessProgressStatus;
import io.harness.gitsync.common.beans.YamlChangeSetEventType;
import io.harness.gitsync.common.dtos.GitToHarnessProgressDTO;
import io.harness.gitsync.common.service.GitToHarnessProgressService;
import io.harness.gitsync.core.dtos.YamlChangeSetDTO;
import io.harness.repositories.gittoharnessstatus.GitToHarnessProgressRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(DX)
public class GitToHarnessProgressServiceImpl implements GitToHarnessProgressService {
  GitToHarnessProgressRepository gitToHarnessProgressRepository;

  @Override
  public GitToHarnessProgressDTO save(GitToHarnessProgressDTO gitToHarnessProgress) {
    final GitToHarnessProgress gitToHarnessProgressEntity = GitToHarnessProgressMapper.toEntity(gitToHarnessProgress);
    return GitToHarnessProgressMapper.writeDTO(gitToHarnessProgressRepository.save(gitToHarnessProgressEntity));
  }

  @Override
  public GitToHarnessProgressDTO update(String uuid, Update update) {
    // Ideally the JPA should have updated the lastUpdatedAt, but due to some limitations
    // they don't update this field. https://github.com/spring-projects/spring-data-mongodb/issues/1797
    update.set(GitToHarnessProgressKeys.lastUpdatedAt, System.currentTimeMillis());
    Criteria criteria = Criteria.where(GitToHarnessProgressKeys.uuid).is(uuid);
    return GitToHarnessProgressMapper.writeDTO(gitToHarnessProgressRepository.findAndModify(criteria, update));
  }

  @Override
  public GitToHarnessProgressDTO updateFilesInProgressRecord(
      String uuid, List<GitToHarnessFileProcessingRequest> gitToHarnessFilesToProcess) {
    Update update = new Update();
    update.set(GitToHarnessProgressKeys.gitFileChanges, gitToHarnessFilesToProcess);
    update.set(GitToHarnessProgressKeys.stepType, GET_FILES);
    update.set(GitToHarnessProgressKeys.stepStatus, DONE);
    return update(uuid, update);
  }

  @Override
  public GitToHarnessProgressDTO updateStepStatus(String uuid, GitToHarnessProcessingStepStatus stepStatus) {
    Update update = new Update();
    update.set(GitToHarnessProgressKeys.stepStatus, stepStatus);
    return update(uuid, update);
  }

  @Override
  public GitToHarnessProgressDTO save(YamlChangeSetDTO yamlChangeSetDTO, YamlChangeSetEventType eventType,
      GitToHarnessProcessingStepType stepType, GitToHarnessProcessingStepStatus stepStatus) {
    GitToHarnessProgressDTO gitToHarnessProgress = GitToHarnessProgressDTO.builder()
                                                       .accountIdentifier(yamlChangeSetDTO.getAccountId())
                                                       .yamlChangeSetId(yamlChangeSetDTO.getChangesetId())
                                                       .repoUrl(yamlChangeSetDTO.getRepoUrl())
                                                       .branch(yamlChangeSetDTO.getBranch())
                                                       .eventType(eventType)
                                                       .stepType(stepType)
                                                       .stepStatus(stepStatus)
                                                       .stepStartingTime(System.currentTimeMillis())
                                                       .build();
    return save(gitToHarnessProgress);
  }

  @Override
  public GitToHarnessProgressDTO startNewStep(String gitToHarnessProgressRecordId,
      GitToHarnessProcessingStepType stepType, GitToHarnessProcessingStepStatus status) {
    Update update = new Update();
    update.set(GitToHarnessProgressKeys.stepType, stepType);
    update.set(GitToHarnessProgressKeys.stepStatus, status);
    update.set(GitToHarnessProgressKeys.stepStartingTime, System.currentTimeMillis());
    return update(gitToHarnessProgressRecordId, update);
  }

  @Override
  public GitToHarnessProgressDTO updateProgressWithProcessingResponse(
      String gitToHarnessProgressRecordId, GitToHarnessProcessingResponse gitToHarnessResponse) {
    Update update = new Update();
    update.addToSet(GitToHarnessProgressKeys.processingResponse, gitToHarnessResponse);
    return update(gitToHarnessProgressRecordId, update);
  }

  @Override
  public boolean isProgressEventAlreadyProcessedOrInProcess(
      String repoURL, String commitId, YamlChangeSetEventType eventType) {
    GitToHarnessProgress gitToHarnessProgress =
        gitToHarnessProgressRepository.findByRepoUrlAndCommitIdAndEventType(repoURL, commitId, eventType);
    if (gitToHarnessProgress == null) {
      return false;
    }
    return !gitToHarnessProgress.getGitToHarnessProgressStatus().isFailureStatus();
  }

  @Override
  public GitToHarnessProgressDTO initProgress(YamlChangeSetDTO yamlChangeSetDTO, YamlChangeSetEventType eventType,
      GitToHarnessProcessingStepType stepType, String commitId) {
    // TODO change it to upsert query
    GitToHarnessProgress gitToHarnessProgress =
        gitToHarnessProgressRepository.findByYamlChangeSetId(yamlChangeSetDTO.getChangesetId());
    if (gitToHarnessProgress != null) {
      return GitToHarnessProgressMapper.writeDTO(gitToHarnessProgress);
    }
    GitToHarnessProgressDTO gitToHarnessProgressToBeSaved =
        GitToHarnessProgressDTO.builder()
            .accountIdentifier(yamlChangeSetDTO.getAccountId())
            .yamlChangeSetId(yamlChangeSetDTO.getChangesetId())
            .repoUrl(yamlChangeSetDTO.getRepoUrl())
            .branch(yamlChangeSetDTO.getBranch())
            .eventType(eventType)
            .stepType(stepType)
            .stepStatus(GitToHarnessProcessingStepStatus.TO_DO)
            .stepStartingTime(System.currentTimeMillis())
            .commitId(commitId)
            .gitToHarnessProgressStatus(GitToHarnessProgressStatus.TO_DO)
            .build();
    return save(gitToHarnessProgressToBeSaved);
  }

  @Override
  public GitToHarnessProgressDTO updateProgressStatus(
      String gitToHarnessProgressRecordId, GitToHarnessProgressStatus gitToHarnessProgressStatus) {
    Update update = new Update();
    update.set(GitToHarnessProgressKeys.gitToHarnessProgressStatus, gitToHarnessProgressStatus);
    return update(gitToHarnessProgressRecordId, update);
  }

  @Override
  public GitToHarnessProgressDTO getBranchSyncStatus(String repoURL, String branch) {
    GitToHarnessProgress gitToHarnessProgress =
        gitToHarnessProgressRepository.findByRepoUrlAndBranchAndEventType(repoURL, branch, BRANCH_SYNC);
    if (gitToHarnessProgress != null) {
      return GitToHarnessProgressMapper.writeDTO(gitToHarnessProgress);
    }
    return null;
  }

  @Override
  public GitToHarnessProgressDTO getByRepoUrlAndCommitIdAndEventType(
      String repoURL, String commitId, YamlChangeSetEventType eventType) {
    GitToHarnessProgress gitToHarnessProgress =
        gitToHarnessProgressRepository.findByRepoUrlAndCommitIdAndEventType(repoURL, commitId, eventType);
    if (gitToHarnessProgress != null) {
      return GitToHarnessProgressMapper.writeDTO(gitToHarnessProgress);
    }
    return null;
  }

  @Override
  public GitToHarnessProgressDTO getByYamlChangeSetId(String yamlChangeSetId) {
    GitToHarnessProgress gitToHarnessProgress = gitToHarnessProgressRepository.findByYamlChangeSetId(yamlChangeSetId);
    if (gitToHarnessProgress != null) {
      return GitToHarnessProgressMapper.writeDTO(gitToHarnessProgress);
    }
    return null;
  }

  @Override
  public GitToHarnessProgressDTO updateProcessingCommitId(String uuid, String processingCommitId) {
    Update update = new Update();
    update.set(GitToHarnessProgressKeys.processingCommitId, processingCommitId);
    return update(uuid, update);
  }

  @Override
  public void deleteByAccount(String accountId) {
    gitToHarnessProgressRepository.deleteAllByAccountIdentifier(accountId);
  }
}
