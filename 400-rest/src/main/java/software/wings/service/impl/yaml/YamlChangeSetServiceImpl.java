/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.mongo.MongoUtils.setUnset;

import static software.wings.yaml.gitSync.YamlChangeSet.MAX_RETRY_COUNT_EXCEEDED_CODE;
import static software.wings.yaml.gitSync.YamlChangeSet.Status.QUEUED;
import static software.wings.yaml.gitSync.YamlChangeSet.Status.SKIPPED;

import static java.lang.Boolean.TRUE;
import static java.lang.String.format;

import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SortOrder.OrderType;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.NoResultFoundException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;

import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.Base;
import software.wings.beans.yaml.GitFileChange;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.yaml.EntityUpdateService;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.service.intfc.yaml.YamlSuccessfulChangeService;
import software.wings.service.intfc.yaml.sync.YamlGitConfigService;
import software.wings.yaml.gitSync.GitSyncMetadata;
import software.wings.yaml.gitSync.GitSyncMetadata.GitSyncMetadataKeys;
import software.wings.yaml.gitSync.GitWebhookRequestAttributes;
import software.wings.yaml.gitSync.YamlChangeSet;
import software.wings.yaml.gitSync.YamlChangeSet.Status;
import software.wings.yaml.gitSync.YamlChangeSet.YamlChangeSetKeys;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.mapping.Mapper;
import dev.morphia.query.CriteriaContainer;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by anubhaw on 10/31/17.
 */
@Singleton
@ValidateOnExecution
@Slf4j
public class YamlChangeSetServiceImpl implements YamlChangeSetService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private PersistentLocker persistentLocker;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private EntityUpdateService entityUpdateService;
  @Inject private YamlGitService yamlGitService;
  @Inject private YamlSuccessfulChangeService yamlSuccessfulChangeService;
  @Inject private YamlGitConfigService yamlGitConfigService;
  public static final Integer MAX_RETRY_COUNT = 3;

  @Override
  public YamlChangeSet save(YamlChangeSet yamlChangeSet) {
    populateGitSyncMetadata(yamlChangeSet);
    final String savedId = wingsPersistence.save(yamlChangeSet);
    final YamlChangeSet savedYamlChangeSet = wingsPersistence.get(YamlChangeSet.class, savedId);
    onYamlChangeSetSave(savedYamlChangeSet);
    return savedYamlChangeSet;
  }

  private void onYamlChangeSetSave(YamlChangeSet savedYamlChangeSet) {
    try {
      if (isGitSyncConfiguredForChangeSet(savedYamlChangeSet)) {
        yamlSuccessfulChangeService.updateOnHarnessChangeSet(savedYamlChangeSet);
      }
    } catch (Exception e) {
      log.error("error while processing onYamlChangeSetSave event", e);
    }
  }
  @Override
  public void populateGitSyncMetadata(YamlChangeSet yamlChangeSet) {
    if (StringUtils.isBlank(yamlChangeSet.getQueueKey()) || yamlChangeSet.getGitSyncMetadata() == null) {
      try {
        final YamlGitConfig yamlGitConfig = getYamlGitConfig(yamlChangeSet);

        yamlChangeSet.setGitSyncMetadata(buildGitSyncMetadata(yamlGitConfig));

        yamlChangeSet.setQueueKey(buildQueueKey(yamlGitConfig));
      } catch (Exception e) {
        log.warn("unable to populate git sync metadata. ignoring these fields", e);
      }
    }
  }

  private GitSyncMetadata buildGitSyncMetadata(YamlGitConfig yamlGitConfig) {
    return GitSyncMetadata.builder()
        .gitConnectorId(yamlGitConfig.getGitConnectorId())
        .repositoryName(yamlGitConfig.getRepositoryName())
        .branchName(yamlGitConfig.getBranchName())
        .yamlGitConfigId(yamlGitConfig.getUuid())
        .build();
  }

  @NotNull
  private YamlGitConfig getYamlGitConfig(YamlChangeSet yamlChangeSet) {
    return yamlChangeSet.isGitToHarness() ? getYamlGitConfigForGitToHarness(yamlChangeSet)
                                          : getYamlGitConfigForHarnessToGit(yamlChangeSet);
  }

  private String buildQueueKey(YamlGitConfig yamlGitConfig) {
    return Arrays
        .asList(yamlGitConfig.getAccountId(), yamlGitConfig.getGitConnectorId(), yamlGitConfig.getRepositoryName(),
            yamlGitConfig.getBranchName())
        .stream()
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.joining(":"));
  }

  @NotNull
  private YamlGitConfig getYamlGitConfigForHarnessToGit(YamlChangeSet yamlChangeSet) {
    final YamlGitConfig yamlGitConfig = yamlGitService.getYamlGitConfigForHarnessToGitChangeSet(yamlChangeSet);
    if (yamlGitConfig == null) {
      throw NoResultFoundException.newBuilder()
          .message(format(
              "Unable to find yamlGitConfig for harness to git changeset for account =[%s], appId=[%s]. Git Sync might not have been configured",
              yamlChangeSet.getAccountId(), yamlChangeSet.getAppId()))
          .build();
    }
    return yamlGitConfig;
  }

  @NotNull
  private YamlGitConfig getYamlGitConfigForGitToHarness(YamlChangeSet yamlChangeSet) {
    final List<YamlGitConfig> yamlGitConfigs = yamlGitService.getYamlGitConfigsForGitToHarnessChangeSet(yamlChangeSet);
    if (isEmpty(yamlGitConfigs)) {
      throw NoResultFoundException.newBuilder()
          .message(format(
              "unable to find yamlGitConfig for git to harness changeset for account =[%s], git ConnectorDisconnectHandler id =[%s], branch=[%s]. Git Sync might not have been configured",
              yamlChangeSet.getAccountId(), yamlChangeSet.getGitWebhookRequestAttributes().getGitConnectorId(),
              yamlChangeSet.getGitWebhookRequestAttributes().getBranchName()))
          .build();
    }
    return yamlGitConfigs.get(0);
  }

  private boolean isGitSyncConfiguredForChangeSet(YamlChangeSet yamlChangeSet) {
    return yamlChangeSet.getGitSyncMetadata() != null;
  }

  @Override
  public long getItemsInQueueKey(String appId, String accountId) {
    YamlGitConfig yamlGitConfig = yamlGitConfigService.getYamlGitConfigFromAppId(appId, accountId);
    if (yamlGitConfig == null) {
      throw NoResultFoundException.newBuilder()
          .message(format(
              "Unable to find yamlGitConfig for account = %s, appId = %s. Git Sync might not have been configured",
              accountId, appId))
          .build();
    }
    String queueKey = buildQueueKey(yamlGitConfig);
    return wingsPersistence.createQuery(YamlChangeSet.class)
        .filter(YamlChangeSetKeys.accountId, yamlGitConfig.getAccountId())
        .filter(YamlChangeSetKeys.queueKey, queueKey)
        .filter(YamlChangeSetKeys.status, QUEUED)
        .count();
  }

  @Override
  public YamlChangeSet get(String accountId, String changeSetId) {
    return wingsPersistence.createQuery(YamlChangeSet.class)
        .filter(YamlChangeSetKeys.accountId, accountId)
        .filter(Mapper.ID_KEY, changeSetId)
        .get();
  }

  @Override
  public void update(YamlChangeSet yamlChangeSet) {
    UpdateOperations<YamlChangeSet> updateOperations =
        wingsPersistence.createUpdateOperations(YamlChangeSet.class).set("status", yamlChangeSet.getStatus());
    wingsPersistence.update(yamlChangeSet, updateOperations);
  }

  private PageResponse<YamlChangeSet> listYamlChangeSets(PageRequest<YamlChangeSet> pageRequest) {
    return wingsPersistence.query(YamlChangeSet.class, pageRequest);
  }

  @Override
  public synchronized YamlChangeSet getQueuedChangeSetForWaitingQueueKey(
      String accountId, String queueKey, int maxRunningChangesetsForAccount) {
    try (AcquiredLock lock = persistentLocker.waitToAcquireLock(
             YamlChangeSet.class, accountId, Duration.ofMinutes(2), Duration.ofSeconds(10))) {
      if (anyChangeSetRunningFoQueueKey(accountId, queueKey)) {
        log.info("Found running changeset for queuekey. Returning null");
        return null;
      }

      if (accountQuotaMaxedOut(accountId, maxRunningChangesetsForAccount)) {
        log.info("Account quota has been reached. Returning null");
        return null;
      }

      final YamlChangeSet selectedChangeSet = selectQueuedChangeSetWithPriority(accountId, queueKey);

      if (selectedChangeSet == null) {
        log.info("No change set found in queued state");
      }

      return selectedChangeSet;
    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception exception) {
      log.error("Error seen in fetching changeSet", exception);
    }
    return null;
  }

  private boolean accountQuotaMaxedOut(String accountId, int maxRunningChangesetsForAccount) {
    return wingsPersistence.createQuery(YamlChangeSet.class)
               .filter(YamlChangeSetKeys.accountId, accountId)
               .filter(YamlChangeSetKeys.status, Status.RUNNING)
               .count()
        >= maxRunningChangesetsForAccount;
  }

  private YamlChangeSet selectQueuedChangeSetWithPriority(String accountId, String queueKey) {
    //      find the head of the queue
    YamlChangeSet selectedYamlChangeSet = null;

    final YamlChangeSet headChangeSet = peekQueueHead(accountId, queueKey);
    if (headChangeSet != null && isFullSync(headChangeSet)) {
      selectedYamlChangeSet = headChangeSet;
    }
    YamlChangeSet fullSyncChangeset = getAnyFullSyncChangeset(accountId, queueKey);
    if (fullSyncChangeset != null) {
      selectedYamlChangeSet = fullSyncChangeset;
    }

    if (selectedYamlChangeSet == null) {
      final YamlChangeSet oldestGitToHarnessChangeSet = getOldestGitToHarnessChangeSet(accountId, queueKey);
      if (oldestGitToHarnessChangeSet != null) {
        selectedYamlChangeSet = oldestGitToHarnessChangeSet;
      }
    }

    if (selectedYamlChangeSet == null) {
      selectedYamlChangeSet = headChangeSet;
    }

    if (selectedYamlChangeSet != null) {
      final boolean updateStatus = updateStatus(accountId, selectedYamlChangeSet.getUuid(), Status.RUNNING);
      if (updateStatus) {
        return get(accountId, selectedYamlChangeSet.getUuid());
      } else {
        log.error("error while updating status of yaml change set Id = [{}]. Skipping selection",
            selectedYamlChangeSet.getUuid());
      }
    }
    return null;
  }

  private boolean isFullSync(YamlChangeSet yamlChangeSet) {
    return yamlChangeSet.isFullSync();
  }

  private YamlChangeSet peekQueueHead(String accountId, String queueKey) {
    return wingsPersistence.createQuery(YamlChangeSet.class)
        .filter(YamlChangeSetKeys.accountId, accountId)
        .filter(YamlChangeSetKeys.queueKey, queueKey)
        .filter(YamlChangeSetKeys.status, Status.QUEUED)
        .project(YamlChangeSetKeys.gitFileChanges, false)
        .order(YamlChangeSetKeys.queuedOn)
        .get();
  }

  private YamlChangeSet getOldestGitToHarnessChangeSet(String accountId, String queueKey) {
    return wingsPersistence.createQuery(YamlChangeSet.class)
        .filter(YamlChangeSetKeys.accountId, accountId)
        .filter(YamlChangeSetKeys.queueKey, queueKey)
        .filter(YamlChangeSetKeys.status, Status.QUEUED)
        .filter(YamlChangeSetKeys.gitToHarness, TRUE)
        .project(YamlChangeSetKeys.gitFileChanges, false)
        .order(YamlChangeSet.CREATED_AT_KEY)
        .get();
  }

  private boolean anyChangeSetRunningFoQueueKey(String accountId, String queueKey) {
    return wingsPersistence.createQuery(YamlChangeSet.class)
               .filter(YamlChangeSetKeys.accountId, accountId)
               .filter(YamlChangeSetKeys.queueKey, queueKey)
               .filter(YamlChangeSetKeys.status, Status.RUNNING)
               .count()
        > 0;
  }

  private YamlChangeSet getAnyFullSyncChangeset(String accountId, String queueKey) {
    return wingsPersistence.createQuery(YamlChangeSet.class)
        .filter(YamlChangeSetKeys.accountId, accountId)
        .filter(YamlChangeSetKeys.queueKey, queueKey)
        .filter(YamlChangeSetKeys.status, Status.QUEUED)
        .filter(YamlChangeSetKeys.fullSync, true)
        .get();
  }

  @Override
  public synchronized List<YamlChangeSet> getChangeSetsToBeMarkedSkipped(String accountId) {
    YamlChangeSet mostRecentCompletedChangeSet = getMostRecentChangeSetWithCompletedStatus(accountId);

    PageRequestBuilder pageRequestBuilder = aPageRequest()
                                                .addFilter("accountId", Operator.EQ, accountId)
                                                .addFilter("status", Operator.IN, new Status[] {Status.QUEUED});

    if (mostRecentCompletedChangeSet != null) {
      pageRequestBuilder.addFilter(
          YamlChangeSet.CREATED_AT_KEY, Operator.GE, mostRecentCompletedChangeSet.getCreatedAt());
    }

    return listYamlChangeSets(pageRequestBuilder.build()).getResponse();
  }

  private YamlChangeSet getMostRecentChangeSetWithCompletedStatus(String accountId) {
    List<YamlChangeSet> changeSetsWithCompletedStatus =
        listYamlChangeSets(aPageRequest()
                               .addFilter("accountId", Operator.EQ, accountId)
                               .addFilter("status", Operator.EQ, Status.COMPLETED)
                               .addOrder(YamlChangeSet.CREATED_AT_KEY, OrderType.DESC)
                               .withLimit("1")
                               .build())
            .getResponse();

    return isNotEmpty(changeSetsWithCompletedStatus) ? changeSetsWithCompletedStatus.get(0) : null;
  }

  @Override
  public boolean updateStatusAndIncrementPushCount(String accountId, String changeSetId, Status newStatus) {
    YamlChangeSet yamlChangeSet = get(accountId, changeSetId);
    if (yamlChangeSet != null) {
      UpdateOperations<YamlChangeSet> updateOperations =
          wingsPersistence.createUpdateOperations(YamlChangeSet.class).set(YamlChangeSetKeys.status, newStatus);
      updateOperations.inc(YamlChangeSetKeys.pushRetryCount);
      UpdateResults status = wingsPersistence.update(yamlChangeSet, updateOperations);
      return status.getUpdatedCount() != 0;
    } else {
      log.warn("No YamlChangeSet found");
    }
    return false;
  }

  @Override
  public boolean updateStatus(String accountId, String changeSetId, Status newStatus) {
    YamlChangeSet yamlChangeSet = get(accountId, changeSetId);
    if (yamlChangeSet != null) {
      UpdateResults status = wingsPersistence.update(
          yamlChangeSet, wingsPersistence.createUpdateOperations(YamlChangeSet.class).set("status", newStatus));
      return status.getUpdatedCount() != 0;
    } else {
      log.warn("No YamlChangeSet found");
    }
    return false;
  }

  /**
   * Update status from RUNNING to COMPLETED or FAILED depending on operation result
   * @param accountId
   * @param newStatus
   * @return
   */
  @Override
  public boolean updateStatusForYamlChangeSets(String accountId, Status newStatus, Status currentStatus) {
    UpdateOperations<YamlChangeSet> ops = wingsPersistence.createUpdateOperations(YamlChangeSet.class);
    setUnset(ops, "status", newStatus);

    Query<YamlChangeSet> yamlChangeSetQuery = wingsPersistence.createQuery(YamlChangeSet.class)
                                                  .filter(YamlChangeSetKeys.accountId, accountId)
                                                  .filter(YamlChangeSetKeys.status, currentStatus);

    UpdateResults status = wingsPersistence.update(yamlChangeSetQuery, ops);

    return status.getUpdatedCount() != 0;
  }

  @Override
  public void markQueuedYamlChangeSetsWithMaxRetriesAsSkipped(String accountId) {
    try (AcquiredLock lock = persistentLocker.waitToAcquireLock(
             YamlChangeSet.class, accountId, Duration.ofMinutes(1), Duration.ofSeconds(10))) {
      markQueuedYamlChangeSetsWithMaxRetriesAsSkipped(accountId, null);
    }
  }

  @Override
  public void markQueuedYamlChangeSetsWithMaxRetriesAsSkipped(String accountId, String changeSetId) {
    UpdateOperations<YamlChangeSet> ops = wingsPersistence.createUpdateOperations(YamlChangeSet.class);
    setUnset(ops, YamlChangeSetKeys.status, SKIPPED);
    setUnset(ops, YamlChangeSetKeys.messageCode, MAX_RETRY_COUNT_EXCEEDED_CODE);

    Query<YamlChangeSet> yamlChangeSetQuery = wingsPersistence.createQuery(YamlChangeSet.class)
                                                  .filter(YamlChangeSetKeys.accountId, accountId)
                                                  .filter(YamlChangeSetKeys.status, QUEUED)
                                                  .field(YamlChangeSetKeys.retryCount)
                                                  .greaterThan(MAX_RETRY_COUNT);
    if (changeSetId != null) {
      yamlChangeSetQuery.filter(Base.ID_KEY2, changeSetId);
    }
    UpdateResults status = wingsPersistence.update(yamlChangeSetQuery, ops);
    log.info(
        "Updated the status of [{}] YamlChangeSets to Skipped. Max retry count exceeded", status.getUpdatedCount());
  }

  @Override
  public boolean updateStatusAndIncrementRetryCountForYamlChangeSets(
      String accountId, Status newStatus, List<Status> currentStatuses, List<String> yamlChangeSetIds) {
    try (AcquiredLock lock = persistentLocker.waitToAcquireLock(
             YamlChangeSet.class, accountId, Duration.ofMinutes(1), Duration.ofSeconds(10))) {
      if (isEmpty(yamlChangeSetIds)) {
        return true;
      }

      UpdateOperations<YamlChangeSet> ops =
          wingsPersistence.createUpdateOperations(YamlChangeSet.class).inc(YamlChangeSetKeys.retryCount);
      setUnset(ops, YamlChangeSetKeys.status, newStatus);

      Query<YamlChangeSet> yamlChangeSetQuery = wingsPersistence.createQuery(YamlChangeSet.class)
                                                    .filter(YamlChangeSetKeys.accountId, accountId)
                                                    .field(YamlChangeSetKeys.status)
                                                    .in(currentStatuses)
                                                    .field("_id")
                                                    .in(yamlChangeSetIds);

      return updateYamlChangeSets(accountId, yamlChangeSetQuery, ops);
    }
  }

  @Override
  public boolean updateStatusForGivenYamlChangeSets(
      String accountId, Status newStatus, List<Status> currentStatuses, List<String> yamlChangeSetIds) {
    try (AcquiredLock lock = persistentLocker.waitToAcquireLock(
             YamlChangeSet.class, accountId, Duration.ofMinutes(1), Duration.ofSeconds(10))) {
      if (isEmpty(yamlChangeSetIds)) {
        return true;
      }

      Query<YamlChangeSet> yamlChangeSetQuery = wingsPersistence.createQuery(YamlChangeSet.class)
                                                    .filter(YamlChangeSetKeys.accountId, accountId)
                                                    .field(YamlChangeSetKeys.status)
                                                    .in(currentStatuses)
                                                    .field("_id")
                                                    .in(yamlChangeSetIds);
      UpdateOperations<YamlChangeSet> ops = wingsPersistence.createUpdateOperations(YamlChangeSet.class);
      setUnset(ops, YamlChangeSetKeys.status, newStatus);

      return updateYamlChangeSets(accountId, yamlChangeSetQuery, ops);
    }
  }

  private boolean updateYamlChangeSets(
      String accountId, Query<YamlChangeSet> query, UpdateOperations<YamlChangeSet> updateOperations) {
    try {
      UpdateResults status = wingsPersistence.update(query, updateOperations);
      return status.getUpdatedCount() != 0;
    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception exception) {
      log.error("Error seen in fetching changeSet", exception);
    }

    return false;
  }

  @Override
  public boolean deleteChangeSet(String accountId, String changeSetId) {
    return wingsPersistence.delete(wingsPersistence.createQuery(YamlChangeSet.class)
                                       .filter(YamlChangeSetKeys.accountId, accountId)
                                       .filter(Mapper.ID_KEY, changeSetId));
  }

  @Override
  public void deleteChangeSets(
      String accountId, Status[] statuses, Integer maxDeleteCount, String batchSize, int retentionPeriodInDays) {
    long cutOffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionPeriodInDays);
    int deletedCount = 0;

    try {
      boolean shouldContinue = true;
      while (shouldContinue && deletedCount < maxDeleteCount) {
        PageRequestBuilder pageRequestBuilder = aPageRequest()
                                                    .addFilter("accountId", Operator.EQ, accountId)
                                                    .addFilter("status", Operator.IN, statuses)
                                                    .addFilter(YamlChangeSet.CREATED_AT_KEY, Operator.LT, cutOffTime)
                                                    .addFieldsIncluded("_id")
                                                    .withLimit(batchSize);

        List<YamlChangeSet> yamlChangeSets = listYamlChangeSets(pageRequestBuilder.build()).getResponse();
        if (isNotEmpty(yamlChangeSets)) {
          List<String> ids = yamlChangeSets.stream().map(Base::getUuid).collect(Collectors.toList());
          wingsPersistence.delete(wingsPersistence.createQuery(YamlChangeSet.class).field("_id").in(ids));
          deletedCount = deletedCount + Integer.parseInt(batchSize);
        } else {
          shouldContinue = false;
        }
      }
    } catch (Exception e) {
      throw new WingsException(ErrorCode.GENERAL_ERROR, WingsException.USER)
          .addParam("message", "deleting YamlChangeSets failed with error: " + e.getMessage());
    }
  }

  @Override
  public <T> YamlChangeSet saveChangeSet(String accountId, List<GitFileChange> gitFileChanges, T entity) {
    if (isEmpty(gitFileChanges)) {
      return null;
    }

    YamlChangeSet yamlChangeSet = YamlChangeSet.builder()
                                      .accountId(accountId)
                                      .gitFileChanges(gitFileChanges)
                                      .status(Status.QUEUED)
                                      .queuedOn(System.currentTimeMillis())
                                      .retryCount(0)
                                      .build();

    yamlChangeSet.setAppId(entityUpdateService.obtainAppIdFromEntity(entity));
    return save(yamlChangeSet);
  }

  @Override
  public List<YamlChangeSet> getChangeSetsWithStatus(String accountId, String appId, YamlGitConfig yamlGitConfig,
      int displayCount, List<YamlChangeSet.Status> statuses, Boolean gitToHarness) {
    Query<YamlChangeSet> query = wingsPersistence.createQuery(YamlChangeSet.class)
                                     .field(YamlChangeSetKeys.status)
                                     .in(statuses)
                                     .filter(YamlChangeSetKeys.accountId, accountId);
    if (gitToHarness == null) {
      query.or(getGitToHarnessChangeSetCriteria(query, yamlGitConfig), getHarnessToGitChangeSetCriteria(query, appId));
    } else if (gitToHarness == true) {
      query.and(getGitToHarnessChangeSetCriteria(query, yamlGitConfig));
    } else {
      query.and(getHarnessToGitChangeSetCriteria(query, appId));
    }
    return query.asList(new FindOptions().limit(displayCount));
  }

  @Override
  public YamlChangeSet pushYamlChangeSetForGitToHarness(
      String accountId, String branchName, String connectorId, String repositoryName, String appId) {
    if (!featureFlagService.isEnabled(FeatureName.CG_GIT_POLLING, accountId)) {
      return null;
    }
    if (!isG2hChangesetQueued(accountId, branchName, connectorId, repositoryName)) {
      YamlChangeSet yamlChangeSet = YamlChangeSet.builder()
                                        .status(YamlChangeSet.Status.QUEUED)
                                        .accountId(accountId)
                                        .fullSync(false)
                                        .gitToHarness(true)
                                        .gitWebhookRequestAttributes(GitWebhookRequestAttributes.builder()
                                                                         .branchName(branchName)
                                                                         .gitConnectorId(connectorId)
                                                                         .repositoryFullName(repositoryName)
                                                                         .isPollingBased(true)
                                                                         .build())
                                        .queuedOn(System.currentTimeMillis())
                                        .appId(appId)
                                        .gitFileChanges(Collections.emptyList())
                                        .build();

      return save(yamlChangeSet);
    } else {
      return null;
    }
  }

  private boolean isG2hChangesetQueued(String accountId, String branchName, String connectorId, String repositoryName) {
    long count =
        wingsPersistence.createQuery(YamlChangeSet.class)
            .filter(YamlChangeSetKeys.status, QUEUED)
            .filter(YamlChangeSetKeys.accountId, accountId)
            .filter(YamlChangeSetKeys.gitSyncMetadata + "." + GitSyncMetadataKeys.gitConnectorId, connectorId)
            .filter(YamlChangeSetKeys.gitSyncMetadata + "." + GitSyncMetadataKeys.branchName, branchName)
            .filter(YamlChangeSetKeys.gitSyncMetadata + "." + GitSyncMetadataKeys.repositoryName, repositoryName)
            .count();
    return count > 0;
  }

  private CriteriaContainer getGitToHarnessChangeSetCriteria(Query<YamlChangeSet> query, YamlGitConfig yamlGitConfig) {
    return query.and(query.criteria(YamlChangeSetKeys.gitToHarness).equal(true),
        query.criteria(YamlChangeSetKeys.gitSyncMetadata + "." + GitSyncMetadataKeys.gitConnectorId)
            .equal(yamlGitConfig.getGitConnectorId()),
        query.criteria(YamlChangeSetKeys.gitSyncMetadata + "." + GitSyncMetadataKeys.branchName)
            .equal(yamlGitConfig.getBranchName()),
        query.criteria(YamlChangeSetKeys.gitSyncMetadata + "." + GitSyncMetadataKeys.repositoryName)
            .equal(yamlGitConfig.getRepositoryName()));
  }

  private CriteriaContainer getHarnessToGitChangeSetCriteria(Query<YamlChangeSet> query, String appId) {
    return query.and(query.criteria(YamlChangeSetKeys.gitToHarness).equal(Boolean.FALSE),
        query.criteria(ApplicationKeys.appId).equal(appId));
  }

  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.delete(
        wingsPersistence.createQuery(YamlChangeSet.class).filter(YamlChangeSetKeys.accountId, accountId));
  }
}
