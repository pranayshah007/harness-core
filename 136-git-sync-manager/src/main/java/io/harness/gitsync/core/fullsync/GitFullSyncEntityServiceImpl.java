/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.fullsync;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.gitsync.core.beans.GitFullSyncEntityInfo.SyncStatus.FAILED;
import static io.harness.gitsync.core.beans.GitFullSyncEntityInfo.SyncStatus.QUEUED;
import static io.harness.gitsync.core.beans.GitFullSyncEntityInfo.SyncStatus.SUCCESS;
import static io.harness.utils.PageUtils.getPageRequest;

import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.core.beans.GitFullSyncEntityInfo;
import io.harness.gitsync.core.beans.GitFullSyncEntityInfo.GitFullSyncEntityInfoKeys;
import io.harness.gitsync.fullsync.dtos.GitFullSyncEntityInfoDTO;
import io.harness.gitsync.fullsync.dtos.GitFullSyncEntityInfoFilterDTO;
import io.harness.gitsync.fullsync.mappers.GitFullSyncEntityInfoMapper;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.EntityDetail.EntityDetailKeys;
import io.harness.repositories.fullSync.GitFullSyncEntityRepository;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(DX)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class GitFullSyncEntityServiceImpl implements GitFullSyncEntityService {
  private final GitFullSyncEntityRepository gitFullSyncEntityRepository;

  @Override
  public GitFullSyncEntityInfo save(GitFullSyncEntityInfo gitFullSyncEntityInfo) {
    return gitFullSyncEntityRepository.save(gitFullSyncEntityInfo);
  }

  @Override
  public void markQueuedOrFailed(
      String uuid, String accountId, long currentRetryCount, long maxRetryCount, String errorMsg) {
    if (currentRetryCount + 1 < maxRetryCount) {
      incrementRetryCountAndMarkQueued(uuid, accountId, errorMsg);
    } else {
      markFailed(uuid, accountId, errorMsg);
    }
  }

  @Override
  public void markSuccessful(String uuid, String accountId) {
    Criteria criteria = new Criteria();
    criteria.and(GitFullSyncEntityInfoKeys.uuid).is(uuid);
    criteria.and(GitFullSyncEntityInfoKeys.accountIdentifier).is(accountId);
    Update update = new Update();
    update.set(GitFullSyncEntityInfoKeys.syncStatus, SUCCESS);
    update.push(GitFullSyncEntityInfoKeys.errorMessage, null);
    gitFullSyncEntityRepository.update(criteria, update);
  }

  @Override
  public List<GitFullSyncEntityInfo> listEntitiesToBeSynced(String accountIdentifier, String messageId) {
    Criteria criteria = Criteria.where(GitFullSyncEntityInfoKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(GitFullSyncEntityInfoKeys.messageId)
                            .is(messageId)
                            .and(GitFullSyncEntityInfoKeys.syncStatus)
                            .in(Arrays.asList(QUEUED, FAILED));
    return gitFullSyncEntityRepository.findAll(criteria);
  }

  @Override
  public Optional<GitFullSyncEntityInfo> get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String filePath) {
    return Optional.ofNullable(
        gitFullSyncEntityRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndFilePath(
            accountIdentifier, orgIdentifier, projectIdentifier, filePath));
  }

  @Override
  public List<GitFullSyncEntityInfo> getQueuedEntitiesFromPreviousJobs(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String currentMessageId) {
    Criteria criteria = Criteria.where(GitFullSyncEntityInfoKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(GitFullSyncEntityInfoKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(GitFullSyncEntityInfoKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(GitFullSyncEntityInfoKeys.messageId)
                            .ne(currentMessageId)
                            .and(GitFullSyncEntityInfoKeys.syncStatus)
                            .is(QUEUED);
    return gitFullSyncEntityRepository.findAll(criteria);
  }

  @Override
  public void updateStatus(String accountIdentifier, String orgIdentifier, String projectIdentifier, String filePath,
      List<GitFullSyncEntityInfo.SyncStatus> oldStatus, GitFullSyncEntityInfo.SyncStatus newStatus) {
    Criteria criteria = Criteria.where(GitFullSyncEntityInfoKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(GitFullSyncEntityInfoKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(GitFullSyncEntityInfoKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(GitFullSyncEntityInfoKeys.filePath)
                            .is(filePath)
                            .and(GitFullSyncEntityInfoKeys.syncStatus)
                            .in(oldStatus);
    Update update = update(GitFullSyncEntityInfoKeys.syncStatus, newStatus);
    gitFullSyncEntityRepository.update(criteria, update);
  }

  private void markFailed(String uuid, String accountId, String errorMsg) {
    Criteria criteria = new Criteria();
    criteria.and(GitFullSyncEntityInfoKeys.uuid).is(uuid);
    criteria.and(GitFullSyncEntityInfoKeys.accountIdentifier).is(accountId);
    Update update = new Update();
    update.set(GitFullSyncEntityInfoKeys.syncStatus, FAILED);
    update.push(GitFullSyncEntityInfoKeys.errorMessage, errorMsg);
    gitFullSyncEntityRepository.update(criteria, update);
  }

  private void incrementRetryCountAndMarkQueued(String uuid, String accountId, String errorMsg) {
    Criteria criteria = new Criteria();
    criteria.and(GitFullSyncEntityInfoKeys.uuid).is(uuid);
    criteria.and(GitFullSyncEntityInfoKeys.accountIdentifier).is(accountId);
    Update update = new Update();
    update.set(GitFullSyncEntityInfoKeys.syncStatus, QUEUED);
    update.inc(GitFullSyncEntityInfoKeys.retryCount);
    update.push(GitFullSyncEntityInfoKeys.errorMessage, errorMsg);
    gitFullSyncEntityRepository.update(criteria, update);
  }

  private Criteria getCriteria(
      String account, String org, String project, GitFullSyncEntityInfoFilterDTO gitFullSyncEntityInfoFilterDTO) {
    Criteria criteria = Criteria.where(GitFullSyncEntityInfoKeys.accountIdentifier)
                            .is(account)
                            .and(GitFullSyncEntityInfoKeys.orgIdentifier)
                            .is(org)
                            .and(GitFullSyncEntityInfoKeys.projectIdentifier)
                            .is(project);

    if (gitFullSyncEntityInfoFilterDTO.getEntityTypes() != null
        && gitFullSyncEntityInfoFilterDTO.getEntityTypes().size() > 0) {
      criteria.and(GitFullSyncEntityInfoKeys.entityDetail + "." + EntityDetailKeys.type)
          .in(gitFullSyncEntityInfoFilterDTO.getEntityTypes());
    }
    if (gitFullSyncEntityInfoFilterDTO.getSyncStatus() != null) {
      criteria.and(GitFullSyncEntityInfoKeys.syncStatus).is(gitFullSyncEntityInfoFilterDTO.getSyncStatus());
    } else {
      criteria.and(GitFullSyncEntityInfoKeys.syncStatus).in(Arrays.asList(SUCCESS, QUEUED, FAILED));
    }
    return criteria;
  }
  private Criteria getCriteria(String account, String org, String project, String searchTerm,
      GitFullSyncEntityInfoFilterDTO gitFullSyncEntityInfoFilterDTO) {
    Criteria criteria = getCriteria(account, org, project, gitFullSyncEntityInfoFilterDTO);
    if (!StringUtils.isEmpty(searchTerm)) {
      criteria.orOperator(Criteria.where(GitFullSyncEntityInfoKeys.filePath).regex(searchTerm, "i"),
          Criteria.where(GitFullSyncEntityInfoKeys.entityDetail + EntityDetailKeys.name).regex(searchTerm, "i"),
          Criteria.where(GitFullSyncEntityInfoKeys.entityDetail + "." + EntityDetailKeys.entityRef + ".branch")
              .regex(searchTerm, "i"));
    }
    return criteria;
  }

  @Override
  public PageResponse<GitFullSyncEntityInfoDTO> list(String account, String org, String project,
      PageRequest pageRequest, String searchTerm, GitFullSyncEntityInfoFilterDTO gitFullSyncEntityInfoFilterDTO) {
    Criteria criteria = getCriteria(account, org, project, searchTerm, gitFullSyncEntityInfoFilterDTO);
    Pageable pageable = getPageRequest(pageRequest);
    return PageUtils.getNGPageResponse(
        gitFullSyncEntityRepository.findAll(criteria, pageable).map(GitFullSyncEntityInfoMapper::toDTO));
  }

  @Override
  public long count(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      GitFullSyncEntityInfoFilterDTO gitFullSyncEntityInfoFilterDTO) {
    return gitFullSyncEntityRepository.count(
        getCriteria(accountIdentifier, orgIdentifier, projectIdentifier, gitFullSyncEntityInfoFilterDTO));
  }

  @Override
  public void updateStatus(
      String accountId, String uuid, GitFullSyncEntityInfo.SyncStatus status, String errorMessage) {
    Criteria criteria = new Criteria();
    criteria.and(GitFullSyncEntityInfoKeys.uuid).is(uuid);
    criteria.and(GitFullSyncEntityInfoKeys.accountIdentifier).is(accountId);
    Update update = new Update();
    update.set(GitFullSyncEntityInfoKeys.syncStatus, status);
    if (errorMessage != null) {
      update.push(GitFullSyncEntityInfoKeys.errorMessage, errorMessage);
    }
    gitFullSyncEntityRepository.update(criteria, update);
  }

  @Override
  public void deleteAll(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = Criteria.where(GitFullSyncEntityInfoKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(GitFullSyncEntityInfoKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(GitFullSyncEntityInfoKeys.projectIdentifier)
                            .is(projectIdentifier);
    gitFullSyncEntityRepository.deleteAll(criteria);
  }
}
