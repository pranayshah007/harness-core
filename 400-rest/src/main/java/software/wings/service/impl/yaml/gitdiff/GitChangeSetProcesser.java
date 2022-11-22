/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.gitdiff;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.manage.GlobalContextManager.ensureGlobalContextGuard;

import static software.wings.beans.yaml.YamlConstants.GIT_YAML_LOG_PREFIX;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;

import io.harness.logging.AccountLogContext;
import io.harness.manage.GlobalContextManager.GlobalContextGuard;
import io.harness.mongo.ProcessTimeLogContext;

import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.GitCommit;
import software.wings.beans.yaml.GitDiffResult;
import software.wings.beans.yaml.GitFileChange;
import software.wings.dl.WingsPersistence;
import software.wings.exception.YamlProcessingException.ChangeWithErrorMsg;
import software.wings.service.impl.yaml.YamlProcessingLogContext;
import software.wings.service.impl.yaml.gitdiff.gitaudit.YamlAuditRecordGenerationUtils;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.service.intfc.yaml.sync.GitSyncService;
import software.wings.yaml.gitSync.GitFileActivity;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class GitChangeSetProcesser {
  @Inject private GitChangeSetHandler gitChangeSetHandler;
  @Inject private YamlAuditRecordGenerationUtils gitChangeAuditRecordHandler;
  @Inject private YamlGitService yamlGitService;
  @Inject private GitSyncService gitSyncService;
  @Inject private YamlHelper yamlHelper;
  @Inject private WingsPersistence wingsPersistence;

  public void processGitChangeSet(String accountId, GitDiffResult gitDiffResult) {
    final Stopwatch stopwatch = Stopwatch.createStarted();
    try (AccountLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         YamlProcessingLogContext ignore2 = YamlProcessingLogContext.builder()
                                                .branchName(gitDiffResult.getBranch())
                                                .repoName(gitDiffResult.getRepoName())
                                                .commitId(gitDiffResult.getCommitId())
                                                .build(OVERRIDE_ERROR)) {
      log.info(GIT_YAML_LOG_PREFIX + "Started processing git diff results with files [{}]",
          emptyIfNull(gitDiffResult.getGitFileChanges()).stream().map(GitFileChange::getFilePath).collect(toList()));

      // ensure gitCommit is not already processed. Else nothing to be done.
      boolean commitAlreadyProcessed = yamlGitService.isCommitAlreadyProcessed(accountId, gitDiffResult.getCommitId());
      if (commitAlreadyProcessed) {
        // do nothing
        log.warn(GIT_YAML_LOG_PREFIX + "Commit [{}] already processed for account {}", gitDiffResult.getCommitId(),
            accountId);
        return;
      }

      // Injest Yaml Changes with Auditing
      ingestYamlChangeWithAudit(accountId, gitDiffResult);

      try (ProcessTimeLogContext ignore3 = new ProcessTimeLogContext(stopwatch.elapsed(MILLISECONDS), OVERRIDE_ERROR)) {
        log.info(GIT_YAML_LOG_PREFIX + "Successfully  processed git diff results");
      }
    }
  }

  private void ingestYamlChangeWithAudit(String accountId, GitDiffResult gitDiffResult) {
    // This will create Audit header record for git sync
    gitChangeAuditRecordHandler.processGitChangesForAudit(accountId, gitDiffResult);

    Map<String, ChangeWithErrorMsg> changeWithErrorMsgs = null;

    try (GlobalContextGuard guard = ensureGlobalContextGuard()) {
      // changeWithErrorMsgs is a map of <YamlPath, ErrorMessage> for failed yaml changes
      final String processingCommitId = gitDiffResult.getCommitId();
      final List<GitFileChange> gitFileChanges = gitDiffResult.getGitFileChanges();
      addYamlChangeSetToFilesCommited(gitFileChanges, gitDiffResult.getYamlGitConfig());
      Set<String> nameOfTheNewAppsAdded = getNamesOfTheNewAppsAdded(gitFileChanges, accountId);
      preProcessGitFileActivityChanges(
          processingCommitId, gitDiffResult.getCommitTimeMs(), gitDiffResult.getCommitMessage(), gitFileChanges);
      changeWithErrorMsgs = gitChangeSetHandler.ingestGitYamlChangs(accountId, gitDiffResult);
      GitCommit.Status status =
          changeWithErrorMsgs.size() > 0 ? GitCommit.Status.COMPLETED_WITH_ERRORS : GitCommit.Status.COMPLETED;
      postProcessGitFileActivityChanges(processingCommitId, accountId, gitFileChanges, status, nameOfTheNewAppsAdded);
      // Finalize audit.
      // 1. Mark exit point.
      // 2. Set status code 200 / 207 (multi-status code (indicating success and failure for some paths)
      // 3. Set detailed message
      gitChangeAuditRecordHandler.finalizeAuditRecord(
          accountId, getFileChangesOfCommit(gitFileChanges), changeWithErrorMsgs);
    }
  }

  private Set<String> getNamesOfTheNewAppsAdded(List<GitFileChange> gitFileChangeList, String accountId) {
    if (isEmpty(gitFileChangeList)) {
      return Collections.emptySet();
    }
    Set<String> appNameSet = gitFileChangeList.stream()
                                 .map(gitFileChange -> yamlHelper.getAppName(gitFileChange.getFilePath()))
                                 .collect(toSet());
    // We can get null appName in case of global cloud provider
    Iterables.removeIf(appNameSet, Objects::isNull);
    Set<String> appsWhichExists = getAppNamesWhichExistsInHarness(appNameSet, accountId);
    if (isNotEmpty(appsWhichExists)) {
      appNameSet.removeAll(appsWhichExists);
    }
    return appNameSet;
  }

  private Set<String> getAppNamesWhichExistsInHarness(Set<String> appNameSet, String accountId) {
    List<Application> applications = wingsPersistence.createQuery(Application.class)
                                         .filter(ApplicationKeys.accountId, accountId)
                                         .field(ApplicationKeys.name)
                                         .in(appNameSet)
                                         .asList();
    if (isEmpty(applications)) {
      return Collections.emptySet();
    }
    return applications.stream().map(app -> app.getName()).collect(toSet());
  }

  private void addYamlChangeSetToFilesCommited(
      List<GitFileChange> gitFileChanges, software.wings.yaml.gitSync.YamlGitConfig yamlGitConfig) {
    if (isEmpty(gitFileChanges)) {
      return;
    }
    gitFileChanges.forEach(gitFileChange -> gitFileChange.setYamlGitConfig(yamlGitConfig));
  }

  /**
   * - Setting up commit details to gitFileChanges
   * - Update git file activity of current commit file changes to QUEUED
   * @param processingCommitId current commit id
   * @param processingCommitTimeMs current time in ms
   * @param commitMessage commit message
   * @param gitFileChanges file change list
   */
  private void preProcessGitFileActivityChanges(String processingCommitId, Long processingCommitTimeMs,
      String commitMessage, List<GitFileChange> gitFileChanges) {
    addProcessingCommitDetailsToChangeList(processingCommitId, processingCommitTimeMs, commitMessage, gitFileChanges);
    // All initial activities will be created with status QUEUED
    gitSyncService.logActivityForGitOperation(
        getFileChangesOfCommit(gitFileChanges), GitFileActivity.Status.QUEUED, true, false, "", "", "");
  }

  private List<GitFileChange> getFileChangesOfCommit(List<GitFileChange> gitFileChanges) {
    if (isEmpty(gitFileChanges)) {
      return gitFileChanges;
    }
    return gitFileChanges.stream().filter(change -> !change.isChangeFromAnotherCommit()).collect(toList());
  }

  private void postProcessGitFileActivityChanges(String processingCommitId, String accountId,
      List<GitFileChange> gitFileChanges, GitCommit.Status status, Set<String> nameOfTheNewAppsAdded) {
    // All remaining activities to be marked as SKIPPED
    gitSyncService.markRemainingFilesAsSkipped(processingCommitId, accountId);
    gitSyncService.changeAppIdOfNewlyAddedFiles(nameOfTheNewAppsAdded, accountId, processingCommitId);
    gitSyncService.createGitFileActivitySummaryForCommit(processingCommitId, accountId, true, status);
  }

  private void addProcessingCommitDetailsToChangeList(String processingCommitId, Long processingCommitTimeMs,
      String processingCommitMessage, List<GitFileChange> gitFileChanges) {
    if (isEmpty(gitFileChanges)) {
      return;
    }
    gitFileChanges.forEach(gitFileChange -> {
      gitFileChange.setProcessingCommitId(processingCommitId);
      gitFileChange.setProcessingCommitTimeMs(processingCommitTimeMs);
      gitFileChange.setProcessingCommitMessage(processingCommitMessage);
      gitFileChange.setChangeFromAnotherCommit(!gitFileChange.getCommitId().equalsIgnoreCase(processingCommitId));
    });
  }
}
