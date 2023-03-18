/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.gitsync.common.beans.YamlChangeSet.MAX_RETRY_COUNT_EXCEEDED_CODE;
import static io.harness.gitsync.common.beans.YamlChangeSetStatus.SKIPPED;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.beans.YamlChangeSet;
import io.harness.gitsync.common.beans.YamlChangeSet.YamlChangeSetKeys;
import io.harness.gitsync.common.beans.YamlChangeSetStatus;
import io.harness.gitsync.core.dtos.YamlChangeSetDTO;
import io.harness.gitsync.core.dtos.YamlChangeSetSaveDTO;
import io.harness.gitsync.core.helper.YamlChangeSetBackOffHelper;
import io.harness.gitsync.core.runnable.ChangeSetGroupingKey;
import io.harness.gitsync.core.runnable.ChangeSetGroupingKey.ChangeSetGroupingKeyKeys;
import io.harness.gitsync.core.service.YamlChangeSetService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.repositories.yamlChangeSet.YamlChangeSetRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.UpdateResult;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.Size;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
@OwnedBy(DX)
@ValidateOnExecution
public class YamlChangeSetServiceImpl implements YamlChangeSetService {
  @Inject private YamlChangeSetRepository yamlChangeSetRepository;
  @Inject private PersistentLocker persistentLocker;

  @Override
  public YamlChangeSetDTO save(YamlChangeSetSaveDTO yamlChangeSet) {
    final YamlChangeSet changeSet = getYamlChangesetFromSaveDTO(yamlChangeSet);
    final YamlChangeSet savedChangeSet = yamlChangeSetRepository.save(changeSet);
    return YamlChangeSetMapper.getYamlChangeSetDto(savedChangeSet);
  }

  private YamlChangeSet getYamlChangesetFromSaveDTO(YamlChangeSetSaveDTO yamlChangeSet) {
    return YamlChangeSet.builder()
        .repoUrl(yamlChangeSet.getRepoUrl())
        .accountId(yamlChangeSet.getAccountId())
        .branch(yamlChangeSet.getBranch())
        .status(YamlChangeSetStatus.QUEUED.name())
        .queueKey(buildQueueKey(yamlChangeSet))
        .gitWebhookRequestAttributes(yamlChangeSet.getGitWebhookRequestAttributes())
        .eventMetadata(yamlChangeSet.getEventMetadata())
        .eventType(yamlChangeSet.getEventType())
        .queuedOn(System.currentTimeMillis())
        .build();
  }

  private String buildQueueKey(YamlChangeSetSaveDTO yamlChangeSet) {
    return String.format(
        "%s:%s:%s", yamlChangeSet.getAccountId(), yamlChangeSet.getRepoUrl(), yamlChangeSet.getBranch());
  }

  @Override
  public Optional<YamlChangeSetDTO> get(String accountId, String changeSetId) {
    final Optional<YamlChangeSet> changeSet = yamlChangeSetRepository.findById(changeSetId);
    return changeSet.map(YamlChangeSetMapper::getYamlChangeSetDto);
  }

  @Override
  public Optional<YamlChangeSetDTO> peekQueueHead(
      String accountId, String queueKey, YamlChangeSetStatus yamlChangeSetStatus) {
    final Optional<YamlChangeSet> changeSet =
        yamlChangeSetRepository.findFirstByAccountIdAndQueueKeyAndStatusOrderByQueuedOn(
            accountId, queueKey, yamlChangeSetStatus.name());
    return changeSet.map(YamlChangeSetMapper::getYamlChangeSetDto);
  }

  public boolean changeSetExistsFoQueueKey(
      String accountId, String queueKey, List<YamlChangeSetStatus> yamlChangeSetStatuses) {
    final List<String> statuses = getStatus(yamlChangeSetStatuses);
    return yamlChangeSetRepository.countByAccountIdAndStatusInAndQueueKey(accountId, statuses, queueKey) > 0;
  }

  private List<String> getStatus(@Size(min = 1) List<YamlChangeSetStatus> yamlChangeSetStatuses) {
    return yamlChangeSetStatuses.stream().map(Enum::name).collect(Collectors.toList());
  }

  @Override
  public int countByAccountIdAndStatus(String accountId, List<YamlChangeSetStatus> yamlChangeSetStatuses) {
    return yamlChangeSetRepository.countByAccountIdAndStatusIn(accountId, getStatus(yamlChangeSetStatuses));
  }

  @Override
  public boolean updateStatus(String accountId, String changeSetId, YamlChangeSetStatus newStatus) {
    Optional<YamlChangeSetDTO> yamlChangeSet = get(accountId, changeSetId);
    if (yamlChangeSet.isPresent()) {
      UpdateResult updateResult =
          yamlChangeSetRepository.updateYamlChangeSetStatus(newStatus, yamlChangeSet.get().getChangesetId());
      return updateResult.getModifiedCount() != 0;
    } else {
      log.warn("No YamlChangeSet found");
    }
    return false;
  }

  @Override
  public boolean markSkippedWithMessageCode(String accountId, String changeSetId, String messageCode) {
    Update update = new Update().set(YamlChangeSetKeys.status, SKIPPED).set(YamlChangeSetKeys.messageCode, messageCode);
    Query query = new Query().addCriteria(
        new Criteria().and(YamlChangeSetKeys.accountId).is(accountId).and(YamlChangeSetKeys.uuid).is(changeSetId));
    final UpdateResult status = yamlChangeSetRepository.update(query, update);
    log.info(
        "Updated the status of [{}] YamlChangeSets to Skipped. Max retry count exceeded", status.getModifiedCount());
    return status.getModifiedCount() == 1;
  }

  @Override
  public boolean updateStatusAndCutoffTime(String accountId, String changeSetId, YamlChangeSetStatus newStatus) {
    Optional<YamlChangeSetDTO> yamlChangeSet = get(accountId, changeSetId);
    if (yamlChangeSet.isPresent()) {
      final long cutOffTime =
          YamlChangeSetBackOffHelper.getCutOffTime(yamlChangeSet.get().getRetryCount(), System.currentTimeMillis());
      UpdateResult updateResult = yamlChangeSetRepository.updateYamlChangeSetStatusAndCutoffTime(
          newStatus, yamlChangeSet.get().getChangesetId(), cutOffTime);
      return updateResult.getModifiedCount() != 0;
    } else {
      log.warn("No YamlChangeSet found");
    }
    return false;
  }

  @Override
  public void markQueuedYamlChangeSetsWithMaxRetriesAsSkipped(String accountId, int maxRetryCount) {
    try (AcquiredLock lock = persistentLocker.waitToAcquireLock(
             YamlChangeSet.class, accountId, Duration.ofMinutes(2), Duration.ofSeconds(10))) {
      Update update = new Update()
                          .set(YamlChangeSetKeys.status, SKIPPED)
                          .set(YamlChangeSetKeys.messageCode, MAX_RETRY_COUNT_EXCEEDED_CODE);
      Query query = new Query().addCriteria(new Criteria()
                                                .and(YamlChangeSetKeys.accountId)
                                                .is(accountId)
                                                .and(YamlChangeSetKeys.retryCount)
                                                .gt(maxRetryCount));
      UpdateResult status = yamlChangeSetRepository.update(query, update);
      log.info(
          "Updated the status of [{}] YamlChangeSets to Skipped. Max retry count exceeded", status.getModifiedCount());
    }
  }

  @Override
  public boolean updateStatusWithRetryCountIncrement(
      String accountId, YamlChangeSetStatus currentStatus, YamlChangeSetStatus newStatus, String yamlChangeSetId) {
    Update updateOps = new Update().set(YamlChangeSetKeys.status, newStatus);
    updateOps.inc(YamlChangeSetKeys.retryCount);

    Query query = new Query(new Criteria()
                                .and(YamlChangeSetKeys.accountId)
                                .is(accountId)
                                .and(YamlChangeSetKeys.uuid)
                                .is(yamlChangeSetId)
                                .and(YamlChangeSetKeys.status)
                                .is(currentStatus));

    return updateYamlChangeSets(accountId, query, updateOps);
  }

  @Override
  public boolean updateStatusAndIncrementRetryCountForYamlChangeSets(String accountId, YamlChangeSetStatus newStatus,
      List<YamlChangeSetStatus> currentStatuses, List<String> yamlChangeSetIds) {
    try (AcquiredLock lock = persistentLocker.waitToAcquireLock(
             YamlChangeSet.class, accountId, Duration.ofMinutes(2), Duration.ofSeconds(10))) {
      if (isEmpty(yamlChangeSetIds)) {
        return true;
      }

      Update updateOps = new Update().set(YamlChangeSetKeys.status, newStatus);
      updateOps.inc(YamlChangeSetKeys.retryCount);

      Query query = new Query(new Criteria()
                                  .and(YamlChangeSetKeys.accountId)
                                  .is(accountId)
                                  .and(YamlChangeSetKeys.status)
                                  .in(currentStatuses)
                                  .and(YamlChangeSetKeys.uuid)
                                  .in(yamlChangeSetIds));

      return updateYamlChangeSets(accountId, query, updateOps);
    }
  }

  @Override
  public boolean updateStatusForGivenYamlChangeSets(String accountId, YamlChangeSetStatus newStatus,
      List<YamlChangeSetStatus> currentStatuses, List<String> yamlChangeSetIds) {
    try (AcquiredLock lock = persistentLocker.waitToAcquireLock(
             YamlChangeSet.class, accountId, Duration.ofMinutes(2), Duration.ofSeconds(10))) {
      if (isEmpty(yamlChangeSetIds)) {
        return true;
      }

      Query query = new Query().addCriteria(new Criteria()
                                                .and(YamlChangeSetKeys.accountId)
                                                .is(accountId)
                                                .and(YamlChangeSetKeys.status)
                                                .in(currentStatuses)
                                                .and(YamlChangeSetKeys.uuid)
                                                .in(yamlChangeSetIds));

      Update updateOps = new Update().set(YamlChangeSetKeys.status, newStatus);

      return updateYamlChangeSets(accountId, query, updateOps);
    }
  }

  private boolean updateYamlChangeSets(String accountId, Query query, Update updateOperations) {
    try {
      UpdateResult status = yamlChangeSetRepository.update(query, updateOperations);
      return status.getModifiedCount() != 0;
    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception exception) {
      log.error("Error seen in fetching changeSet", exception);
    }

    return false;
  }

  @Override
  public List<YamlChangeSetDTO> findByAccountIdsStatusCutoffLessThan(List<String> runningAccountIdList,
      @Size(min = 1) List<YamlChangeSetStatus> yamlChangeSetStatuses, long cutOffTime) {
    final List<String> statuses = getStatus(yamlChangeSetStatuses);
    final List<YamlChangeSet> changeSets = yamlChangeSetRepository.findByAccountIdInAndStatusInAndCutOffTimeLessThan(
        runningAccountIdList, statuses, cutOffTime);
    return changeSets.stream().map(YamlChangeSetMapper::getYamlChangeSetDto).collect(Collectors.toList());
  }

  @Override
  public List<String> findDistinctAccountIdsByStatus(List<YamlChangeSetStatus> status) {
    return yamlChangeSetRepository.findDistinctAccountIdByStatusIn(status);
  }

  @Override
  public UpdateResult updateYamlChangeSetsToNewStatusWithMessageCodeAndCreatedAtLessThan(
      YamlChangeSetStatus oldStatus, YamlChangeSetStatus newStatus, long timeout, String messageCode) {
    return yamlChangeSetRepository.updateYamlChangeSetsToNewStatusWithMessageCodeAndQueuedAtLessThan(
        oldStatus, newStatus, timeout, messageCode);
  }

  private <C> AggregationResults aggregate(Aggregation aggregation, Class<C> castClass) {
    return yamlChangeSetRepository.aggregate(aggregation, castClass);
  }

  @Override
  public Set<ChangeSetGroupingKey> getChangesetGroupingKeys(Criteria criteria) {
    Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(criteria),
        Aggregation.group(YamlChangeSetKeys.accountId, YamlChangeSetKeys.queueKey)
            .first(YamlChangeSetKeys.accountId)
            .as(YamlChangeSetKeys.accountId)
            .first(YamlChangeSetKeys.queueKey)
            .as(YamlChangeSetKeys.queueKey)
            .count()
            .as(ChangeSetGroupingKeyKeys.count));
    AggregationResults<ChangeSetGroupingKey> aggregationResults = aggregate(aggregation, ChangeSetGroupingKey.class);

    final Set<ChangeSetGroupingKey> keys = new HashSet<>();
    aggregationResults.iterator().forEachRemaining(keys::add);
    return keys;
  }

  @Override
  public List<YamlChangeSet> list(String queueKey, String accountId, YamlChangeSetStatus status) {
    return yamlChangeSetRepository.findByAccountIdAndQueueKeyAndStatus(accountId, queueKey, status.name());
  }

  @Override
  public void markQueuedYamlChangeSetsWithMaxRetriesAsSkipped(int maxRetryCount) {
    Update update = new Update()
                        .set(YamlChangeSetKeys.status, SKIPPED)
                        .set(YamlChangeSetKeys.messageCode, MAX_RETRY_COUNT_EXCEEDED_CODE);
    Query query = new Query().addCriteria(new Criteria()
                                              .and(YamlChangeSetKeys.status)
                                              .is(YamlChangeSetStatus.QUEUED)
                                              .and(YamlChangeSetKeys.retryCount)
                                              .gt(maxRetryCount));
    final UpdateResult status = yamlChangeSetRepository.update(query, update);
    log.info(
        "Updated the status of [{}] YamlChangeSets to Skipped. Max retry count exceeded", status.getModifiedCount());
  }

  @Override
  public void deleteByAccount(String accountId) {
    yamlChangeSetRepository.deleteAllByAccountId(accountId);
  }
}
