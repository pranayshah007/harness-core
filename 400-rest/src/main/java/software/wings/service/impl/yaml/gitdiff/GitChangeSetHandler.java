/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.gitdiff;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.GitCommit.Status.COMPLETED;
import static software.wings.beans.GitCommit.Status.COMPLETED_WITH_ERRORS;
import static software.wings.service.impl.GitConfigHelperService.matchesRepositoryName;
import static software.wings.yaml.gitSync.beans.YamlGitConfig.BRANCH_NAME_KEY;
import static software.wings.yaml.gitSync.beans.YamlGitConfig.GIT_CONNECTOR_ID_KEY;
import static software.wings.yaml.gitSync.beans.YamlGitConfig.REPOSITORY_NAME_KEY;

import static java.lang.Boolean.FALSE;
import static java.util.Collections.emptySet;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.collections4.MapUtils.emptyIfNull;

import io.harness.exception.UnexpectedException;

import software.wings.beans.Application;
import software.wings.beans.CGConstants;
import software.wings.beans.GitCommit;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitDiffResult;
import software.wings.beans.yaml.GitFileChange;
import software.wings.dl.WingsPersistence;
import software.wings.exception.YamlProcessingException;
import software.wings.exception.YamlProcessingException.ChangeWithErrorMsg;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.yaml.YamlDirectoryService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.service.intfc.yaml.sync.GitSyncService;
import software.wings.service.intfc.yaml.sync.YamlService;
import software.wings.yaml.gitSync.GitFileActivity;
import software.wings.yaml.gitSync.YamlChangeSet;
import software.wings.yaml.gitSync.YamlChangeSet.Status;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import com.google.inject.Inject;
import com.mongodb.DuplicateKeyException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.mongodb.morphia.annotations.Transient;

@Slf4j
public class GitChangeSetHandler {
  @Transient @Inject private YamlService yamlService;
  @Transient @Inject private YamlGitService yamlGitService;
  @Transient @Inject private WingsPersistence wingsPersistence;
  @Transient @Inject private YamlDirectoryService yamlDirectoryService;
  @Transient @Inject private AppService appService;
  @Transient @Inject GitSyncService gitSyncService;
  @Transient @Inject ChangeSetRequestTimeFilter changeSetRequestTimeFilter;

  public Map<String, ChangeWithErrorMsg> ingestGitYamlChangs(String accountId, GitDiffResult gitDiffResult) {
    // Mark invalid files as SKIPPED
    final List<GitFileChange> gitDiffResultChangeSet = gitDiffResult.getGitFileChanges();

    final List<GitFileChange> validFilesBasedOnYamlGitConfigFilter = obtainValidGitFileChangesBasedOnYamlGitConfig(
        gitDiffResult.getYamlGitConfig(), gitDiffResultChangeSet, accountId);

    gitSyncService.logActivityForSkippedFiles(
        validFilesBasedOnYamlGitConfigFilter, gitDiffResult, "application folder configured incorrectly", accountId);

    final List<GitFileChange> validFilesAfterChangeRequestTimeFilter =
        applyChangeRequestTimeFilterAndLogActivity(validFilesBasedOnYamlGitConfigFilter, accountId);

    final List<GitFileChange> gitFileChangeList = validFilesAfterChangeRequestTimeFilter;

    applySyncFromGit(gitFileChangeList);

    try {
      final List<ChangeContext> fileChangeContexts = yamlService.processChangeSet(gitFileChangeList);
      log.info("Processed ChangeSet [{}] for account {}", fileChangeContexts, accountId);

      saveProcessedCommit(gitDiffResult, accountId, COMPLETED);
      // this is for GitCommandType.DIFF, where we set gitToHarness = true explicitly as we are responding to
      // webhook invocation
      removeGitSyncErrorsForSuccessfulFiles(gitFileChangeList, emptySet(), accountId);

    } catch (YamlProcessingException ex) {
      log.warn("Unable to process git commit {} for account {}. ", gitDiffResult.getCommitId(), accountId, ex);
      // this is for GitCommandType.DIFF, where we set gitToHarness = true explicitly as we are responding to
      // webhook invocation
      final Map<String, ChangeWithErrorMsg> failedYamlFileChangeMap = ex.getFailedYamlFileChangeMap();
      populatateYamlGitconfigInError(failedYamlFileChangeMap, gitDiffResult.getYamlGitConfig());
      yamlGitService.processFailedChanges(accountId, failedYamlFileChangeMap, true);
      gitSyncService.logActivitiesForFailedChanges(
          failedYamlFileChangeMap, accountId, false, gitDiffResult.getCommitMessage());
      removeGitSyncErrorsForSuccessfulFiles(gitFileChangeList, ex.getFailedYamlFileChangeMap().keySet(), accountId);
      // Add to gitCommits a failed commit.
      saveProcessedCommit(gitDiffResult, accountId, COMPLETED_WITH_ERRORS);

      return failedYamlFileChangeMap;
    }
    return Collections.emptyMap();
  }

  private List<GitFileChange> applyChangeRequestTimeFilterAndLogActivity(
      List<GitFileChange> gitFileChanges, String accountId) {
    final YamlFilterResult yamlFilterResult = changeSetRequestTimeFilter.filterFiles(gitFileChanges, accountId);
    logFileActivityForSkippedFiles(
        gitFileChanges, emptyIfNull(yamlFilterResult.getExcludedFilePathWithReasonMap()), accountId);
    return yamlFilterResult.getFilteredFiles();
  }

  private void logFileActivityForSkippedFiles(
      List<GitFileChange> allGitFileChanges, Map<String, String> excludedFilePathWithReasonMap, String accountId) {
    final Map<String, GitFileChange> allFilesPathToObjectMapping =
        allGitFileChanges.stream().collect(Collectors.toMap(GitFileChange::getFilePath, identity()));

    excludedFilePathWithReasonMap.forEach((filePath, skipMessage) -> {
      final GitFileChange gitFileChange = allFilesPathToObjectMapping.get(filePath);
      if (filePartOfSameCommit(gitFileChange)) {
        gitSyncService.updateStatusOfGitFileActivity(gitFileChange.getProcessingCommitId(),
            Collections.singletonList(gitFileChange.getFilePath()), GitFileActivity.Status.SKIPPED, skipMessage,
            accountId);
      }
    }

    );
  }

  private boolean filePartOfSameCommit(GitFileChange gitFileChange) {
    return !ofNullable(gitFileChange.isChangeFromAnotherCommit()).orElse(FALSE);
  }

  private void populatateYamlGitconfigInError(Map<String, ChangeWithErrorMsg> failedYamlFileChangeMap,
      software.wings.yaml.gitSync.YamlGitConfig yamlGitConfig) {
    emptyIfNull(failedYamlFileChangeMap)
        .values()
        .stream()
        .map(changeWithErrorMsg -> (GitFileChange) changeWithErrorMsg.getChange())
        .filter(gitFileChange -> gitFileChange.getYamlGitConfig() == null)
        .forEach(gitFileChange -> gitFileChange.setYamlGitConfig(yamlGitConfig));
  }

  private void saveProcessedCommit(GitDiffResult gitDiffResult, String accountId, GitCommit.Status gitCommitStatus) {
    final List<String> yamlGitConfigIds =
        yamlGitService.getYamlGitConfigIds(accountId, gitDiffResult.getYamlGitConfig().getGitConnectorId(),
            gitDiffResult.getYamlGitConfig().getBranchName(), gitDiffResult.getYamlGitConfig().getRepositoryName());

    saveCommitFromGit(gitDiffResult, yamlGitConfigIds, accountId, gitCommitStatus);
  }

  private void removeGitSyncErrorsForSuccessfulFiles(
      List<GitFileChange> gitFileChangeList, Set<String> failedFilePathSet, String accountId) {
    final List<GitFileChange> successfullyProcessedFileList =
        emptyIfNull(getSuccessfullyProcessedFiles(gitFileChangeList, failedFilePathSet));
    log.info("Successfully processed files =[{}]",
        successfullyProcessedFileList.stream().map(GitFileChange::getFilePath).collect(toList()));

    yamlGitService.removeGitSyncErrors(accountId, successfullyProcessedFileList, true);
  }

  private List<GitFileChange> getSuccessfullyProcessedFiles(
      List<GitFileChange> gitFileChangeList, Set<String> failedFilePathSet) {
    if (CollectionUtils.isEmpty(failedFilePathSet)) {
      return gitFileChangeList;
    }
    return emptyIfNull(gitFileChangeList)
        .stream()
        .filter(gitFileChange -> !failedFilePathSet.contains(gitFileChange.getFilePath()))
        .collect(toList());
  }

  private List<GitFileChange> obtainValidGitFileChangesBasedOnYamlGitConfig(
      software.wings.yaml.gitSync.YamlGitConfig yamlGitConfig, List<GitFileChange> gitFileChanges, String accountId) {
    List<GitFileChange> gitFileChangeList = new ArrayList<>();
    Map<String, YamlGitConfig> appMap = new HashMap<>();

    if (isEmpty(gitFileChanges)) {
      return gitFileChangeList;
    }

    for (GitFileChange gitFileChange : gitFileChanges) {
      YamlGitConfig currentEntityYamlGitConfig;

      if (yamlGitService.checkApplicationChange(gitFileChange)) {
        // Handles application

        if (!yamlGitService.checkApplicationNameIsValid(gitFileChange)) {
          log.info("Skipping the file {} from processing as it contains invalid app name", gitFileChange.getFilePath());
          continue;
        }

        String appName = yamlGitService.obtainAppNameFromGitFileChange(gitFileChange);
        notNullCheck("Application name cannot be null", USER);

        if (!appMap.containsKey(appName)) {
          Application app = appService.getAppByName(gitFileChange.getAccountId(), appName);

          if (app != null) {
            YamlGitConfig appYamlGitConfig = yamlDirectoryService.weNeedToPushChanges(accountId, app.getUuid());
            appMap.put(appName, appYamlGitConfig);
          } else {
            // New app is created on git side. We need to consume those changes

            gitFileChange.setYamlGitConfig(yamlGitConfig);
            gitFileChangeList.add(gitFileChange);
            continue;
          }
        }

        currentEntityYamlGitConfig = appMap.get(appName);
      } else {
        // Handle account level entities
        // This check is there to make sure that the yamlGitConfig is not disabled. If its disabled then we don't need
        // to process this change.
        currentEntityYamlGitConfig = yamlDirectoryService.weNeedToPushChanges(accountId, GLOBAL_APP_ID);
      }

      if (currentEntityYamlGitConfig != null
          && yamlGitConfig.getGitConnectorId().equals(currentEntityYamlGitConfig.getGitConnectorId())
          && yamlGitConfig.getBranchName().equals(currentEntityYamlGitConfig.getBranchName())
          && matchesRepositoryName(yamlGitConfig.getRepositoryName(), currentEntityYamlGitConfig.getRepositoryName())) {
        gitFileChangeList.add(gitFileChange);
      }
    }

    return gitFileChangeList;
  }

  // TODO: Change it as a part of ContextThreadLocal
  private void applySyncFromGit(List<GitFileChange> gitFileChangeList) {
    if (isEmpty(gitFileChangeList)) {
      return;
    }

    for (GitFileChange gitFileChange : gitFileChangeList) {
      gitFileChange.setSyncFromGit(true);
    }
  }

  public List<String> obtainYamlGitConfigIds(
      String accountId, String branchName, String repositoryName, String gitConnectorId) {
    return wingsPersistence.createQuery(YamlGitConfig.class)
        .filter(CGConstants.ACCOUNT_ID_KEY, accountId)
        .filter(GIT_CONNECTOR_ID_KEY, gitConnectorId)
        .filter(REPOSITORY_NAME_KEY, repositoryName)
        .filter(BRANCH_NAME_KEY, branchName)
        .project(CGConstants.ID_KEY, true)
        .asList()
        .stream()
        .map(YamlGitConfig::getUuid)
        .collect(toList());
  }

  private void saveCommitFromGit(
      GitDiffResult gitDiffResult, List<String> yamlGitConfigIds, String accountId, GitCommit.Status gitCommitStatus) {
    software.wings.yaml.gitSync.YamlGitConfig yamlGitConfig = gitDiffResult.getYamlGitConfig();
    String commitId = gitDiffResult.getCommitId();
    if (yamlGitConfig == null) {
      throw new UnexpectedException(
          String.format("Error while saving commit for commitId=[%s] as the yamlGitConfig is null ", commitId));
    }
    saveGitCommit(GitCommit.builder()
                      .accountId(accountId)
                      .yamlChangeSet(YamlChangeSet.builder()
                                         .accountId(accountId)
                                         .appId(GLOBAL_APP_ID)
                                         .gitToHarness(true)
                                         .status(Status.COMPLETED)
                                         .gitFileChanges(gitDiffResult.getGitFileChanges())
                                         .retryCount(0)
                                         .build())
                      .yamlGitConfigIds(yamlGitConfigIds)
                      .status(gitCommitStatus)
                      .commitId(commitId)
                      .gitConnectorId(yamlGitConfig.getGitConnectorId())
                      .repositoryName(yamlGitConfig.getRepositoryName())
                      .branchName(yamlGitConfig.getBranchName())
                      .gitCommandResult(gitDiffResult)
                      .commitMessage(gitDiffResult.getCommitMessage())
                      .build());
  }

  private void saveGitCommit(GitCommit gitCommit) {
    try {
      yamlGitService.saveCommit(gitCommit);
    } catch (Exception e) {
      if (e instanceof DuplicateKeyException) {
        log.info("This was already persisted in DB. May Happens when 2 successive commits"
            + " are made to git in short duration, and when 2nd commit is done before gitDiff"
            + " for 1st one is in progress");
      } else {
        log.warn("Failed to save gitCommit", e);
        // Try again without gitChangeSet and CommandResults.
        gitCommit.getYamlChangeSet().setGitFileChanges(null);
        gitCommit.setGitCommandResult(null);

        yamlGitService.saveCommit(gitCommit);
      }
    }
  }
}
