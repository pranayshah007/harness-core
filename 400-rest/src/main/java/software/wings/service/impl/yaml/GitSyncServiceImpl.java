/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.CreatedAtAware.CREATED_AT_KEY;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.security.UserGroup.ACCOUNT_ID_KEY;
import static software.wings.beans.yaml.YamlConstants.APPLICATIONS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.beans.yaml.YamlConstants.SETUP_FOLDER;
import static software.wings.service.impl.GitConfigHelperService.UNKNOWN_GIT_CONNECTOR;
import static software.wings.service.impl.yaml.sync.GitSyncErrorUtils.EMPTY_STR;
import static software.wings.service.impl.yaml.sync.GitSyncErrorUtils.getCommitIdOfError;
import static software.wings.service.impl.yaml.sync.GitSyncErrorUtils.getCommitMessageOfError;
import static software.wings.service.impl.yaml.sync.GitSyncErrorUtils.getYamlContentOfError;

import static dev.morphia.aggregation.Group.first;
import static dev.morphia.aggregation.Group.grouping;
import static dev.morphia.aggregation.Projection.projection;
import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SortOrder;
import io.harness.data.structure.EmptyPredicate;
import io.harness.git.model.ChangeType;

import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.GitCommit;
import software.wings.beans.GitConfig;
import software.wings.beans.GitDetail;
import software.wings.beans.GitFileActivitySummary;
import software.wings.beans.GitFileActivitySummary.GitFileActivitySummaryKeys;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.GitDiffResult;
import software.wings.beans.yaml.GitFileChange;
import software.wings.dl.WingsPersistence;
import software.wings.exception.YamlProcessingException.ChangeWithErrorMsg;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.yaml.gitsync.ChangeSetDTO;
import software.wings.service.impl.yaml.gitsync.ChangesetInformation;
import software.wings.service.impl.yaml.gitsync.QueuedChangesetInformation;
import software.wings.service.impl.yaml.gitsync.RunningChangesetInformation;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.impl.yaml.sync.GitSyncRBACHelper;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.service.intfc.yaml.sync.GitSyncService;
import software.wings.service.intfc.yaml.sync.YamlGitConfigService;
import software.wings.service.intfc.yaml.sync.YamlService;
import software.wings.yaml.errorhandling.GitSyncError;
import software.wings.yaml.gitSync.GitFileActivity;
import software.wings.yaml.gitSync.GitFileActivity.GitFileActivityBuilder;
import software.wings.yaml.gitSync.GitFileActivity.GitFileActivityKeys;
import software.wings.yaml.gitSync.GitFileActivity.Status;
import software.wings.yaml.gitSync.GitFileActivity.TriggeredBy;
import software.wings.yaml.gitSync.GitFileProcessingSummary;
import software.wings.yaml.gitSync.GitWebhookRequestAttributes;
import software.wings.yaml.gitSync.YamlChangeSet;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.AggregationOptions;
import dev.morphia.aggregation.AggregationPipeline;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * git sync service.
 */
@ValidateOnExecution
@Singleton
@Slf4j
public class GitSyncServiceImpl implements GitSyncService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private GitConfigHelperService gitConfigHelperService;
  @Inject private YamlGitConfigService yamlGitConfigService;
  @Inject private YamlGitService yamlGitService;
  @Inject private YamlChangeSetService yamlChangeSetService;
  @Inject private YamlService yamlService;
  @Inject private YamlHelper yamlHelper;
  @Inject private YamlSuccessfulChangeServiceImpl yamlSuccessfulChangeService;
  @Inject private GitSyncRBACHelper gitSyncRBACHelper;

  @Override
  public PageResponse<GitFileActivity> fetchGitSyncActivity(
      PageRequest<GitFileActivity> req, String accountId, String appId, boolean activityForFileHistory) {
    if (isNotEmpty(appId)) {
      req.addFilter(GitFileActivityKeys.appId, EQ, appId);
    }
    if (activityForFileHistory) {
      req.addOrder(GitFileActivityKeys.createdAt, SortOrder.OrderType.DESC);
    }
    PageResponse<GitFileActivity> response = wingsPersistence.queryAnalytics(GitFileActivity.class, req);
    List<GitFileActivity> gitFileActivities = response.getResponse();
    List<GitFileActivity> gitFileActivitiesFilteredByAccountRBAC = gitFileActivities;
    if (whetherWeCanHaveAccountLevelFile(appId)) {
      gitFileActivitiesFilteredByAccountRBAC =
          gitSyncRBACHelper.populateUserHasPermissionForFileField(gitFileActivities, accountId);
    }
    populateConnectorNameInFileHistory(gitFileActivitiesFilteredByAccountRBAC, accountId);

    if (!activityForFileHistory) {
      sortGitFileActivityInProcessingOrder(gitFileActivitiesFilteredByAccountRBAC);
    }
    response.setResponse(gitFileActivitiesFilteredByAccountRBAC);
    return response;
  }

  private boolean whetherWeCanHaveAccountLevelFile(String appId) {
    return isEmpty(appId) || GLOBAL_APP_ID.equals(appId);
  }

  private void sortGitFileActivityInProcessingOrder(List<GitFileActivity> gitFileActivities) {
    gitFileActivities.sort(new GitFileActivityComparator());
  }

  private final class GitFileActivityComparator implements Comparator<GitFileActivity> {
    @Override
    public int compare(GitFileActivity lhs, GitFileActivity rhs) {
      int lCoefficient = lhs.getChangeType() == ChangeType.DELETE ? -1 : 1;
      int rlCoefficient = rhs.getChangeType() == ChangeType.DELETE ? -1 : 1;
      return (lCoefficient * yamlService.findOrdinal(lhs.getFilePath(), lhs.getAccountId()))
          - (rlCoefficient * yamlService.findOrdinal(rhs.getFilePath(), rhs.getAccountId()));
    }
  }

  private void populateConnectorNameInFileHistory(List<GitFileActivity> gitFileActivities, String accountId) {
    if (isEmpty(gitFileActivities)) {
      return;
    }
    List<String> connectorIdList = gitFileActivities.stream().map(GitFileActivity::getGitConnectorId).collect(toList());
    Map<String, SettingAttribute> gitConnectorMap = getGitConnectorMap(connectorIdList, accountId);
    gitFileActivities.forEach(fileActivity -> {
      String connectorName = getConnectorNameFromConnectorMap(fileActivity.getGitConnectorId(), gitConnectorMap);
      GitConfig gitConfig = getGitConfigFromConnectorMap(fileActivity.getGitConnectorId(), gitConnectorMap);
      fileActivity.setConnectorName(connectorName);
      fileActivity.setRepositoryInfo(
          gitConfigHelperService.createRepositoryInfo(gitConfig, fileActivity.getRepositoryName()));
    });
  }

  public List<GitFileActivity> getActivitiesForGitSyncErrors(final List<GitSyncError> errors, Status status) {
    if (EmptyPredicate.isEmpty(errors)) {
      return Collections.emptyList();
    }
    return errors.stream()
        .map(error
            -> GitFileActivity.builder()
                   .accountId(error.getAccountId())
                   .commitId(getCommitIdOfError(error))
                   .filePath(error.getYamlFilePath())
                   .fileContent(getYamlContentOfError(error))
                   .status(status)
                   .commitMessage(getCommitMessageOfError(error))
                   .gitConnectorId(error.getGitConnectorId())
                   .repositoryName(error.getRepositoryName())
                   .branchName(error.getBranchName())
                   .triggeredBy(GitFileActivity.TriggeredBy.USER)
                   .build())
        .collect(toList());
  }

  public void logActivitiesForFailedChanges(Map<String, ChangeWithErrorMsg> failedYamlFileChangeMap, String accountId,
      boolean isFullSync, String commitMessage) {
    if (isEmpty(failedYamlFileChangeMap.values())) {
      return;
    }
    List<ChangeWithErrorMsg> failuresWhichArePartOfCommit = new ArrayList<>();
    List<ChangeWithErrorMsg> extraFilesFailedWhileProcessing = new ArrayList<>();
    failedYamlFileChangeMap.values().forEach(changeDetails -> {
      if (isChangePartOfCommit(changeDetails)) {
        failuresWhichArePartOfCommit.add(changeDetails);
      } else {
        extraFilesFailedWhileProcessing.add(changeDetails);
      }
    });
    addActivityForExtraErrorsIfMessageChanged(extraFilesFailedWhileProcessing, isFullSync, commitMessage, accountId);
    updateStatusOnProcessingFailure(failuresWhichArePartOfCommit, accountId);
  }

  private boolean isChangePartOfCommit(ChangeWithErrorMsg changeWithErrorMsg) {
    return !((GitFileChange) changeWithErrorMsg.getChange()).isChangeFromAnotherCommit();
  }

  private void updateStatusOnProcessingFailure(List<ChangeWithErrorMsg> changeWithErrorMsgs, String accountId) {
    if (isEmpty(changeWithErrorMsgs)) {
      return;
    }
    changeWithErrorMsgs.parallelStream().forEach(changeWithErrorMsg -> {
      GitFileChange change = (GitFileChange) changeWithErrorMsg.getChange();
      if (isChangeFromGit(change)) {
        updateStatusOfGitFileActivity(change.getProcessingCommitId(), Arrays.asList(change.getFilePath()),
            GitFileActivity.Status.FAILED, changeWithErrorMsg.getErrorMsg(), accountId);
      }
    });
  }

  private void addActivityForExtraErrorsIfMessageChanged(
      List<ChangeWithErrorMsg> changesFailed, boolean isFullSync, String commitMessage, String accountId) {
    if (isEmpty(changesFailed)) {
      return;
    }

    List<String> nameOfFilesProcessedInCommit = getNameOfFilesProcessed(changesFailed);
    Map<String, String> latestActivitiesForFiles = getLatestActivitiesForFiles(nameOfFilesProcessedInCommit, accountId);
    changesFailed.parallelStream().forEach(failedChange
        -> createFileActivityIfErrorChanged(failedChange, latestActivitiesForFiles, isFullSync, commitMessage));
  }

  private void createFileActivityIfErrorChanged(ChangeWithErrorMsg changeWithErrorMsg,
      Map<String, String> latestActivitiesForFiles, boolean isFullSync, String commitMessage) {
    String newErrorMessage = changeWithErrorMsg.getErrorMsg();
    GitFileChange change = (GitFileChange) changeWithErrorMsg.getChange();
    String filePath = change.getFilePath();
    if (latestActivitiesForFiles.containsKey(filePath)) {
      if (!StringUtils.defaultIfBlank(latestActivitiesForFiles.get(filePath), "").equals(newErrorMessage)) {
        GitFileActivity newFileActivity = createGitFileActivityForFailedExtraFile(change, newErrorMessage, isFullSync);
        wingsPersistence.save(newFileActivity);
      }
    } else {
      log.info(
          "Unexpected Behaviour while processing extra error in commit: No file activity found for file {}", filePath);
    }
  }

  private GitFileActivity createGitFileActivityForFailedExtraFile(
      GitFileChange change, String errorMessage, boolean isFullSync) {
    return buildBaseGitFileActivity(change, "", "")
        .status(Status.FAILED)
        .errorMessage(errorMessage)
        .triggeredBy(getTriggeredBy(change.isSyncFromGit(), isFullSync))
        .commitMessage(change.getCommitMessage())
        .build();
  }

  private List<String> getNameOfFilesProcessed(List<ChangeWithErrorMsg> changeWithErrorMsgs) {
    if (isEmpty(changeWithErrorMsgs)) {
      return Collections.emptyList();
    }
    return changeWithErrorMsgs.stream()
        .map(changeWithErrorMsg -> changeWithErrorMsg.getChange().getFilePath())
        .collect(Collectors.toList());
  }

  private Map<String, String> getLatestActivitiesForFiles(List<String> filePaths, String accountId) {
    @Getter
    class LatestErrorInGitFileActivity {
      String filePath;
      String errorMessage;
    }

    Map<String, String> fileNameErrorMap = new HashMap<>();
    Query<GitFileActivity> query = wingsPersistence.createQuery(GitFileActivity.class)
                                       .filter(ACCOUNT_ID_KEY, accountId)
                                       .field(GitFileActivityKeys.filePath)
                                       .in(filePaths);
    AggregationPipeline aggregationPipeline =
        wingsPersistence.getDatastore(GitFileActivity.class)
            .createAggregation(GitFileActivity.class)
            .match(query)
            .sort(Sort.descending(CREATED_AT_KEY))
            .group(GitFileActivityKeys.filePath,
                grouping(GitFileActivityKeys.errorMessage, first(GitFileActivityKeys.errorMessage)))
            .project(projection(GitFileActivityKeys.filePath, ID_KEY),
                projection(GitFileActivityKeys.errorMessage, GitFileActivityKeys.errorMessage));
    int limit = wingsPersistence.getMaxDocumentLimit(GitFileActivity.class);
    if (limit > 0) {
      aggregationPipeline.limit(limit);
    }
    aggregationPipeline
        .aggregate(LatestErrorInGitFileActivity.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(GitFileActivity.class), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(
            fileErrorPair -> fileNameErrorMap.put(fileErrorPair.getFilePath(), fileErrorPair.getErrorMessage()));
    return fileNameErrorMap;
  }

  public boolean isChangeFromGit(Change change) {
    try {
      return change.isSyncFromGit() && change instanceof GitFileChange
          && isNotEmpty(((GitFileChange) change).getCommitId())
          && isNotEmpty(((GitFileChange) change).getProcessingCommitId());
    } catch (Exception ex) {
      log.error(format("Error while checking if change is from git: %s", ex));
    }
    return false;
  }

  public void onGitFileProcessingSuccess(Change change, String accountId) {
    if (isChangeFromGit(change)) {
      GitFileChange gitFileChange = (GitFileChange) change;
      if (gitFileChange.isChangeFromAnotherCommit()) {
        logActivityForGitOperation(
            singletonList((GitFileChange) change), GitFileActivity.Status.SUCCESS, true, false, "", "", "");
      } else {
        updateStatusOfGitFileActivity(((GitFileChange) change).getProcessingCommitId(),
            singletonList(change.getFilePath()), GitFileActivity.Status.SUCCESS, "", accountId);
      }

      try {
        yamlSuccessfulChangeService.updateOnSuccessfulGitChangeProcessing(gitFileChange, accountId);
      } catch (Exception e) {
        log.error(format("error while updating successful change for file [%s]", gitFileChange.getFilePath()), e);
      }
    }
  }

  @Override
  public List<GitDetail> fetchRepositoriesAccessibleToUser(String accountId) {
    List<YamlGitConfig> yamlGitConfigs = yamlGitConfigService.getYamlGitConfigAccessibleToUserWithEntityName(accountId);

    if (EmptyPredicate.isEmpty(yamlGitConfigs)) {
      return Collections.emptyList();
    }

    // SettingAttribute collection
    List<String> gitConnectorIds =
        yamlGitConfigs.stream().map(YamlGitConfig::getGitConnectorId).collect(Collectors.toList());
    Map<String, SettingAttribute> gitConnectorMap = getGitConnectorMap(gitConnectorIds, accountId);
    return createGitDetails(yamlGitConfigs, gitConnectorMap);
  }

  /**
   * @param gitConnectorIds List of git connector ids
   * @param accountId AccountId
   * @return Returns Map of uuid, SettingAttributes of GitConnector containing uuid, name and value keys.
   */
  @Override
  public Map<String, SettingAttribute> getGitConnectorMap(List<String> gitConnectorIds, String accountId) {
    List<SettingAttribute> gitConnectors = wingsPersistence.createQuery(SettingAttribute.class)
                                               .filter(ACCOUNT_ID_KEY, accountId)
                                               .field(ID_KEY)
                                               .in(gitConnectorIds)
                                               .project(SettingAttributeKeys.uuid, true)
                                               .project(SettingAttributeKeys.name, true)
                                               .project(SettingAttributeKeys.value, true)
                                               .asList();
    if (isEmpty(gitConnectors)) {
      return Collections.emptyMap();
    }
    return gitConnectors.stream().collect(Collectors.toMap(SettingAttribute::getUuid, Function.identity()));
  }

  private List<GitDetail> createGitDetails(
      List<YamlGitConfig> yamlGitConfigs, Map<String, SettingAttribute> gitConnectorMap) {
    if (isEmpty(yamlGitConfigs)) {
      return Collections.emptyList();
    }
    return yamlGitConfigs.stream()
        .map(yamlGitConfig -> createGitDetail(yamlGitConfig, gitConnectorMap, EMPTY_STR))
        .collect(Collectors.toList());
  }

  private GitDetail createGitDetail(
      YamlGitConfig yamlGitConfig, Map<String, SettingAttribute> gitConnectorMap, String gitCommitId) {
    String gitConnectorName = getConnectorNameFromConnectorMap(yamlGitConfig.getGitConnectorId(), gitConnectorMap);
    GitConfig gitConfig = getGitConfigFromConnectorMap(yamlGitConfig.getGitConnectorId(), gitConnectorMap);
    return buildGitDetail(yamlGitConfig, gitConnectorName, gitConfig, gitCommitId);
  }

  private GitDetail buildGitDetail(
      YamlGitConfig yamlGitConfig, String gitConnectorName, GitConfig gitConfig, String gitCommitId) {
    GitDetail gitDetail = buildGitDetail(yamlGitConfig, gitConnectorName, gitCommitId);
    gitDetail.setRepositoryInfo(
        gitConfigHelperService.createRepositoryInfo(gitConfig, yamlGitConfig.getRepositoryName()));
    return gitDetail;
  }

  @Override
  public String getConnectorNameFromConnectorMap(String gitConnectorId, Map<String, SettingAttribute> gitConnectorMap) {
    SettingAttribute attribute = gitConnectorMap.get(gitConnectorId);
    if (null != attribute) {
      return attribute.getName();
    }
    return UNKNOWN_GIT_CONNECTOR;
  }

  @Override
  public GitConfig getGitConfigFromConnectorMap(String gitConnectorId, Map<String, SettingAttribute> gitConnectorMap) {
    SettingAttribute attribute = gitConnectorMap.get(gitConnectorId);
    if (null != attribute && attribute.getValue() instanceof GitConfig) {
      return (GitConfig) attribute.getValue();
    }
    return null;
  }

  private GitDetail buildGitDetail(YamlGitConfig yamlGitConfig, String gitConnectorName, String gitCommitId) {
    return GitDetail.builder()
        .branchName(yamlGitConfig.getBranchName())
        .entityName(yamlGitConfig.getEntityName())
        .entityType(yamlGitConfig.getEntityType())
        .gitConnectorId(yamlGitConfig.getGitConnectorId())
        .connectorName(gitConnectorName)
        .yamlGitConfigId(yamlGitConfig.getUuid())
        .appId(yamlGitConfig.getAppId())
        .gitCommitId(gitCommitId)
        .build();
  }

  @Override
  public PageResponse<GitFileActivitySummary> fetchGitCommits(
      PageRequest<GitFileActivitySummary> pageRequest, Boolean gitToHarness, String accountId, String appId) {
    if (gitToHarness != null) {
      pageRequest.addFilter(GitFileActivitySummaryKeys.gitToHarness, EQ, gitToHarness);
    }
    PageResponse<GitFileActivitySummary> pageResponse =
        wingsPersistence.queryAnalytics(GitFileActivitySummary.class, pageRequest);
    List<GitFileActivitySummary> gitFileActivitySummaries = pageResponse.getResponse();
    populateConnectorNameInGitFileActivitySummaries(gitFileActivitySummaries, accountId);
    pageResponse.setResponse(gitFileActivitySummaries);
    return pageResponse;
  }

  @Override
  public boolean deleteGitCommits(List<String> gitFileActivitySummaryIds, String accountId) {
    return wingsPersistence.deleteOnServer(wingsPersistence.createQuery(GitFileActivitySummary.class)
                                               .filter(GitFileActivitySummaryKeys.accountId, accountId)
                                               .field(GitFileActivitySummaryKeys.uuid)
                                               .in(gitFileActivitySummaryIds));
  }

  public boolean deleteGitCommitsBeforeTime(long expiryTime, String accountId) {
    return wingsPersistence.deleteOnServer(wingsPersistence.createQuery(GitFileActivitySummary.class)
                                               .filter(GitFileActivitySummaryKeys.accountId, accountId)
                                               .field(GitFileActivitySummaryKeys.createdAt)
                                               .lessThan(expiryTime));
  }

  @Override
  public boolean deleteGitActivity(List<String> gitFileActivityIds, String accountId) {
    return wingsPersistence.deleteOnServer(wingsPersistence.createQuery(GitFileActivity.class)
                                               .filter(GitFileActivityKeys.accountId, accountId)
                                               .field(GitFileActivityKeys.uuid)
                                               .in(gitFileActivityIds));
  }
  @Override
  public boolean deleteGitActivityBeforeTime(long time, String accountId) {
    return wingsPersistence.deleteOnServer(wingsPersistence.createQuery(GitFileActivity.class)
                                               .filter(GitFileActivityKeys.accountId, accountId)
                                               .field(GitFileActivityKeys.createdAt)
                                               .lessThan(time));
  }

  private void populateConnectorNameInGitFileActivitySummaries(
      List<GitFileActivitySummary> gitFileActivitySummaries, String accountId) {
    if (isEmpty(gitFileActivitySummaries)) {
      return;
    }
    List<String> connectorIdList =
        gitFileActivitySummaries.stream().map(GitFileActivitySummary::getGitConnectorId).collect(toList());
    Map<String, SettingAttribute> gitConnectorMap = getGitConnectorMap(connectorIdList, accountId);
    gitFileActivitySummaries.forEach(summary -> {
      String connectorName = getConnectorNameFromConnectorMap(summary.getGitConnectorId(), gitConnectorMap);
      GitConfig gitConfig = getGitConfigFromConnectorMap(summary.getGitConnectorId(), gitConnectorMap);
      summary.setConnectorName(connectorName);
      summary.setRepositoryInfo(gitConfigHelperService.createRepositoryInfo(gitConfig, summary.getRepositoryName()));
    });
  }

  private TriggeredBy getTriggeredBy(boolean isGitToHarness, boolean isFullSync) {
    if (isGitToHarness) {
      return TriggeredBy.GIT;
    } else {
      if (isFullSync) {
        return TriggeredBy.FULL_SYNC;
      } else {
        return TriggeredBy.USER;
      }
    }
  }

  @Override
  public void logActivityForGitOperation(List<GitFileChange> changeList, Status status, boolean isGitToHarness,
      boolean isFullSync, String message, String commitId, String commitMessage) {
    try {
      if (isEmpty(changeList)) {
        return;
      }
      String accountId = changeList.get(0).getAccountId();
      Map<String, String> fileNameAppIdMap = getAppIdsForTheGitFileChanges(changeList, accountId);
      final List<GitFileActivity> activities = changeList.stream()
                                                   .map(change
                                                       -> buildBaseGitFileActivity(change, commitId, commitMessage)
                                                              .status(status)
                                                              .errorMessage(message)
                                                              .triggeredBy(getTriggeredBy(isGitToHarness, isFullSync))
                                                              .appId(fileNameAppIdMap.get(change.getFilePath()))
                                                              .build())
                                                   .collect(toList());
      wingsPersistence.save(activities);
    } catch (Exception ex) {
      log.error(format("Error while saving activities: %s", ex));
    }
  }

  Map<String, String> getAppIdsForTheGitFileChanges(List<GitFileChange> changeList, String accountId) {
    Map<String, String> fileNameAppIdMap = new HashMap<>();
    Map<String, String> appNameAppIdMap = new HashMap<>();
    for (GitFileChange change : changeList) {
      String fileName = change.getFilePath();
      String appName = yamlHelper.getAppName(fileName);
      if (appName == null) {
        // The file name doesn't follow appName pattern, it must be a global entity
        fileNameAppIdMap.put(fileName, GLOBAL_APP_ID);
      } else {
        // The file name follows application name pattern
        if (appNameAppIdMap.containsKey(appName)) {
          fileNameAppIdMap.put(fileName, appNameAppIdMap.get(appName));
        } else {
          String appId = yamlService.obtainAppIdFromGitFileChange(accountId, fileName);
          if (GLOBAL_APP_ID.equals(appId)) {
            // The application was deleted/renamed that's why we couldn't get the appId
            if (change.getYamlGitConfig() != null) {
              appId = change.getYamlGitConfig().getAppId();
            }
          }
          appNameAppIdMap.put(appName, appId);
          fileNameAppIdMap.put(fileName, appId);
        }
      }
    }
    return fileNameAppIdMap;
  }

  @Override
  public void updateStatusOfGitFileActivity(
      final String commitId, final List<String> fileNames, Status status, String message, String accountId) {
    if (EmptyPredicate.isEmpty(fileNames)) {
      return;
    }
    try {
      UpdateOperations<GitFileActivity> op = wingsPersistence.createUpdateOperations(GitFileActivity.class)
                                                 .set(GitFileActivityKeys.status, status)
                                                 .set(GitFileActivityKeys.errorMessage, message);
      wingsPersistence.update(wingsPersistence.createQuery(GitFileActivity.class)
                                  .filter(ACCOUNT_ID_KEY, accountId)
                                  .filter(GitFileActivityKeys.processingCommitId, commitId)
                                  .field(GitFileActivityKeys.filePath)
                                  .in(fileNames),
          op);
    } catch (Exception ex) {
      log.error(format("Error while saving activities for commitId: %s", "commitId"));
    }
  }

  @Override
  public void logActivityForSkippedFiles(
      List<GitFileChange> changeList, GitDiffResult gitDiffResult, String message, String accountId) {
    List<GitFileChange> completeChangeList = new ArrayList<>(gitDiffResult.getGitFileChanges());
    completeChangeList = ListUtils.removeAll(completeChangeList, changeList);
    if (isNotEmpty(completeChangeList)) {
      updateStatusOfGitFileActivity(gitDiffResult.getCommitId(),
          completeChangeList.stream().map(gitFileChange -> gitFileChange.getFilePath()).collect(Collectors.toList()),
          Status.SKIPPED, message, accountId);
    }
  }

  private GitFileActivityBuilder buildBaseGitFileActivity(GitFileChange change, String commitId, String commitMessage) {
    software.wings.yaml.gitSync.YamlGitConfig gitConfig = change.getYamlGitConfig();
    String commitIdToPersist = StringUtils.isEmpty(commitId) ? change.getCommitId() : commitId;
    String processingCommitIdToPersist = StringUtils.isEmpty(commitId) ? change.getProcessingCommitId() : commitId;
    String commitMessageToPersist = StringUtils.isEmpty(commitMessage) ? change.getCommitMessage() : commitMessage;
    String processingCommitMessage =
        StringUtils.isEmpty(commitMessage) ? change.getProcessingCommitMessage() : commitMessage;
    final Boolean changeFromAnotherCommit = change.isChangeFromAnotherCommit();
    return GitFileActivity.builder()
        .accountId(change.getAccountId())
        .commitId(commitIdToPersist)
        .processingCommitId(processingCommitIdToPersist)
        .filePath(change.getFilePath())
        .fileContent(change.getFileContent())
        .commitMessage(commitMessageToPersist)
        .processingCommitMessage(processingCommitMessage)
        .changeType(change.getChangeType())
        .gitConnectorId(gitConfig.getGitConnectorId())
        .repositoryName(gitConfig.getRepositoryName())
        .branchName(gitConfig.getBranchName())
        .changeFromAnotherCommit(changeFromAnotherCommit != null
                ? changeFromAnotherCommit
                : !processingCommitIdToPersist.equalsIgnoreCase(commitIdToPersist));
  }

  private GitFileActivitySummary buildBaseGitFileActivitySummary(
      GitFileActivity gitFileActivity, String appId, Boolean gitToHarness, GitCommit.Status status) {
    return GitFileActivitySummary.builder()
        .accountId(gitFileActivity.getAccountId())
        .appId(appId)
        .commitId(gitFileActivity.getCommitId())
        .gitConnectorId(gitFileActivity.getGitConnectorId())
        .repositoryName(gitFileActivity.getRepositoryName())
        .branchName(gitFileActivity.getBranchName())
        .commitMessage(gitFileActivity.getCommitMessage())
        .gitToHarness(gitToHarness)
        .status(status)
        .build();
  }

  private GitFileProcessingSummary createFileProcessingSummary(@NotEmpty List<GitFileActivity> gitFileActivites) {
    final Map<Status, Long> statusToCountMap =
        gitFileActivites.stream()
            .map(gitFileActivity -> gitFileActivity.getStatus())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

    final Long totalCount = statusToCountMap.values().stream().reduce(0L, Long::sum);

    return GitFileProcessingSummary.builder()
        .failureCount(statusToCountMap.getOrDefault(Status.FAILED, 0L))
        .successCount(statusToCountMap.getOrDefault(Status.SUCCESS, 0L))
        .skippedCount(statusToCountMap.getOrDefault(Status.SKIPPED, 0L))
        .queuedCount(statusToCountMap.getOrDefault(Status.QUEUED, 0L))
        .totalCount(totalCount)
        .build();
  }

  public void createGitFileActivitySummaryForCommit(
      final String commitId, final String accountId, Boolean gitToHarness, GitCommit.Status status) {
    try {
      List<GitFileActivity> gitFileActivities = getFileActivitesForCommit(commitId, accountId);
      if (isEmpty(gitFileActivities)) {
        return;
      }
      Map<String, List<GitFileActivity>> appIdFileActivitesMap = groupFileActivitiesAccordingToAppId(gitFileActivities);
      for (Map.Entry<String, List<GitFileActivity>> appIdFileChangeEntry : appIdFileActivitesMap.entrySet()) {
        createGitFileActivitySummaryForApp(
            appIdFileChangeEntry.getKey(), appIdFileChangeEntry.getValue(), gitToHarness, status);
      }
    } catch (Exception ex) {
      log.error(format("Error while saving git file processing summary for commitId: %s", commitId), ex);
    }
  }

  private List<GitFileActivity> getFileActivitesForCommit(String commitId, String accountId) {
    return wingsPersistence.createQuery(GitFileActivity.class)
        .filter(ACCOUNT_ID_KEY, accountId)
        .filter(GitFileActivityKeys.commitId, commitId)
        .asList();
  }

  private Map<String, List<GitFileActivity>> groupFileActivitiesAccordingToAppId(
      List<GitFileActivity> gitFileActivities) {
    if (isEmpty(gitFileActivities)) {
      return new HashMap<>();
    }
    return gitFileActivities.stream().collect(Collectors.groupingBy(GitFileActivity::getAppId, Collectors.toList()));
  }

  private void createGitFileActivitySummaryForApp(
      String appId, List<GitFileActivity> gitFileActivities, Boolean gitToHarness, GitCommit.Status status) {
    if (isEmpty(gitFileActivities)) {
      return;
    }
    GitFileActivity gitFileActivity = gitFileActivities.get(0);
    GitFileActivitySummary gitFileActivitySummary =
        buildBaseGitFileActivitySummary(gitFileActivity, appId, gitToHarness, status);
    gitFileActivitySummary.setFileProcessingSummary(createFileProcessingSummary(gitFileActivities));
    wingsPersistence.save(gitFileActivitySummary);
  }

  public void markRemainingFilesAsSkipped(String commitId, String accountId) {
    try {
      log.warn(format("Few files still in QUEUED status after git processing of commitId: %s", commitId));
      UpdateOperations<GitFileActivity> op = wingsPersistence.createUpdateOperations(GitFileActivity.class)
                                                 .set(GitFileActivityKeys.status, Status.SKIPPED);
      wingsPersistence.update(wingsPersistence.createQuery(GitFileActivity.class)
                                  .filter(ACCOUNT_ID_KEY, accountId)
                                  .filter(GitFileActivityKeys.processingCommitId, commitId)
                                  .filter(GitFileActivityKeys.status, Status.QUEUED),
          op);
    } catch (Exception ex) {
      log.error(format("Error while saving activities for commitId: %s", "commitId"), ex);
    }
  }

  @Override
  public List<ChangeSetDTO> getCommitsWhichAreBeingProcessed(
      String accountId, String appId, int displayCount, Boolean gitToHarness) {
    YamlGitConfig yamlGitConfig = yamlGitService.fetchYamlGitConfig(appId, accountId);
    if (yamlGitConfig == null) {
      return null;
    }
    final List<YamlChangeSet.Status> processingStatuses =
        Arrays.asList(YamlChangeSet.Status.QUEUED, YamlChangeSet.Status.RUNNING);

    List<YamlChangeSet> changeSetsWithProcessingStatus;
    if (gitToHarness != null && !gitToHarness) {
      changeSetsWithProcessingStatus = Collections.emptyList();
    } else {
      changeSetsWithProcessingStatus = yamlChangeSetService.getChangeSetsWithStatus(
          accountId, appId, yamlGitConfig, displayCount, processingStatuses, true);
    }

    List<String> connectorIds =
        changeSetsWithProcessingStatus.stream()
            .filter(ycs -> null != ycs.getGitSyncMetadata() && null != ycs.getGitSyncMetadata().getGitConnectorId())
            .map(ycs -> ycs.getGitSyncMetadata().getGitConnectorId())
            .collect(toList());

    Map<String, SettingAttribute> gitConnectorMap = getGitConnectorMap(connectorIds, accountId);

    return changeSetsWithProcessingStatus.stream()
        .map(changeSet -> {
          if (YamlChangeSet.Status.QUEUED.equals(changeSet.getStatus())) {
            return makeDTOForQueuedChangeSet(changeSet, yamlGitConfig, gitConnectorMap);
          } else {
            return makeDTOForRunningChangeSet(changeSet, yamlGitConfig, gitConnectorMap);
          }
        })
        .collect(Collectors.toList());
  }

  private ChangeSetDTO makeDTOForRunningChangeSet(
      YamlChangeSet yamlChangeSet, YamlGitConfig yamlGitConfig, Map<String, SettingAttribute> gitConnectorMap) {
    String gitCommitId = getGitCommitId(yamlChangeSet);
    GitDetail gitDetail = createGitDetail(yamlGitConfig, gitConnectorMap, gitCommitId);
    RunningChangesetInformation runningChangesetInformation = RunningChangesetInformation.builder()
                                                                  .startedRunningAt(yamlChangeSet.getLastUpdatedAt())
                                                                  .queuedAt(yamlChangeSet.getCreatedAt())
                                                                  .build();
    return buildChangesetDTO(runningChangesetInformation, gitDetail, yamlChangeSet.isGitToHarness(),
        YamlChangeSet.Status.RUNNING, yamlChangeSet.getUuid());
  }

  private ChangeSetDTO makeDTOForQueuedChangeSet(
      YamlChangeSet yamlChangeSet, YamlGitConfig yamlGitConfig, Map<String, SettingAttribute> gitConnectorMap) {
    String gitCommitId = getGitCommitId(yamlChangeSet);
    GitDetail gitDetail = createGitDetail(yamlGitConfig, gitConnectorMap, gitCommitId);
    QueuedChangesetInformation queuedChangesetInformation =
        QueuedChangesetInformation.builder().queuedAt(yamlChangeSet.getCreatedAt()).build();
    return buildChangesetDTO(queuedChangesetInformation, gitDetail, yamlChangeSet.isGitToHarness(),
        YamlChangeSet.Status.QUEUED, yamlChangeSet.getUuid());
  }

  private String getGitCommitId(YamlChangeSet yamlChangeSet) {
    if (yamlChangeSet.isGitToHarness()) {
      GitWebhookRequestAttributes gitWebhookRequest = yamlChangeSet.getGitWebhookRequestAttributes();
      if (gitWebhookRequest != null) {
        String commitId = gitWebhookRequest.getHeadCommitId();
        if (isNotBlank(commitId)) {
          return commitId;
        }
      }
    }
    return EMPTY_STR;
  }

  private ChangeSetDTO buildChangesetDTO(ChangesetInformation changesetInformation, GitDetail gitDetail,
      boolean gitToHarness, YamlChangeSet.Status status, String id) {
    return ChangeSetDTO.builder()
        .changesetInformation(changesetInformation)
        .gitDetail(gitDetail)
        .gitToHarness(gitToHarness)
        .status(status)
        .changeSetId(id)
        .build();
  }

  private SettingAttribute getGitConnectorFromId(String gitConnectorId, String accountId) {
    return wingsPersistence.createQuery(SettingAttribute.class)
        .filter(ACCOUNT_ID_KEY, accountId)
        .filter(ID_KEY, gitConnectorId)
        .project(SettingAttributeKeys.uuid, true)
        .project(SettingAttributeKeys.name, true)
        .get();
  }

  public void createGitFileSummaryForFailedOrSkippedCommit(GitCommit gitCommit, boolean gitToHarness) {
    List<GitFileActivitySummary> gitFileActiivitySummary = new ArrayList<>();
    Set<String> appIds = yamlGitConfigService.getAppIdsForYamlGitConfig(gitCommit.getYamlGitConfigIds());
    if (isEmpty(appIds)) {
      return;
    }
    for (String appId : appIds) {
      gitFileActiivitySummary.add(getGitFileActivitySummary(gitCommit, gitToHarness, appId));
    }
    wingsPersistence.save(gitFileActiivitySummary);
  }

  private GitFileActivitySummary getGitFileActivitySummary(GitCommit gitCommit, boolean gitToHarness, String appId) {
    return GitFileActivitySummary.builder()
        .accountId(gitCommit.getAccountId())
        .appId(appId)
        .commitId(gitCommit.getCommitId())
        .gitConnectorId(gitCommit.getGitConnectorId())
        .repositoryName(gitCommit.getRepositoryName())
        .branchName(gitCommit.getBranchName())
        .commitMessage(gitCommit.getCommitMessage())
        .gitToHarness(gitToHarness)
        .status(gitCommit.getStatus())
        .build();
  }

  public void changeAppIdOfNewlyAddedFiles(
      Set<String> nameOfNewAppsInCommit, String accountId, String processingCommitId) {
    if (EmptyPredicate.isEmpty(nameOfNewAppsInCommit)) {
      return;
    }
    Map<String, String> appNameAppIdMapOfNewAppsAdded = getAppNameAppIdMap(nameOfNewAppsInCommit, accountId);
    try {
      for (Map.Entry<String, String> appNameAppIdPair : appNameAppIdMapOfNewAppsAdded.entrySet()) {
        UpdateOperations<GitFileActivity> op = wingsPersistence.createUpdateOperations(GitFileActivity.class)
                                                   .set(GitFileActivityKeys.appId, appNameAppIdPair.getValue());
        wingsPersistence.update(wingsPersistence.createQuery(GitFileActivity.class)
                                    .filter(ACCOUNT_ID_KEY, accountId)
                                    .filter(GitFileActivityKeys.processingCommitId, processingCommitId)
                                    .field(GitFileActivityKeys.filePath)
                                    .startsWith(getAppNamePrefix(appNameAppIdPair.getKey())),
            op);
      }
    } catch (Exception ex) {
      log.error(format("Error while updating appId for commitId: %s, appSet: %s, exception: %s", processingCommitId,
          nameOfNewAppsInCommit.toString(), ex.getMessage()));
    }
  }

  private String getAppNamePrefix(String appName) {
    return SETUP_FOLDER + PATH_DELIMITER + APPLICATIONS_FOLDER + PATH_DELIMITER + appName + PATH_DELIMITER;
  }

  private Map<String, String> getAppNameAppIdMap(Set<String> appNames, String accountId) {
    if (isEmpty(appNames)) {
      return Collections.emptyMap();
    }
    List<Application> applications = wingsPersistence.createQuery(Application.class)
                                         .filter(ACCOUNT_ID_KEY, accountId)
                                         .field(ApplicationKeys.name)
                                         .in(appNames)
                                         .asList();
    return applications.stream().collect(toMap(app -> app.getName(), app -> app.getUuid()));
  }
}
