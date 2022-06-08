/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.yaml.GitCommand.GitCommandType.COMMIT_AND_PUSH;
import static software.wings.beans.yaml.GitCommand.GitCommandType.DIFF;
import static software.wings.beans.yaml.GitFileChange.Builder.aGitFileChange;
import static software.wings.service.impl.yaml.YamlProcessingLogContext.CHANGESET_ID;
import static software.wings.service.impl.yaml.sync.GitSyncErrorUtils.getCommitIdOfError;
import static software.wings.service.impl.yaml.sync.GitSyncErrorUtils.getCommitMessageOfError;
import static software.wings.service.impl.yaml.sync.GitSyncErrorUtils.getCommitTimeOfError;
import static software.wings.service.impl.yaml.sync.GitSyncErrorUtils.getYamlContentOfError;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.delegate.beans.NoAvailableDelegatesException;
import io.harness.delegate.beans.NoInstalledDelegatesException;
import io.harness.eraro.ErrorCode;
import io.harness.exception.UnexpectedException;
import io.harness.git.model.ChangeType;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallbackWithErrorHandling;

import software.wings.beans.GitCommit;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.GitConnectionErrorAlert;
import software.wings.beans.yaml.GitCommand.GitCommandType;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitCommandExecutionResponse.GitCommandStatus;
import software.wings.beans.yaml.GitCommandResult;
import software.wings.beans.yaml.GitCommitAndPushResult;
import software.wings.beans.yaml.GitCommitRequest;
import software.wings.beans.yaml.GitDiffResult;
import software.wings.beans.yaml.GitFileChange;
import software.wings.service.impl.yaml.gitdiff.GitChangeSetHandler;
import software.wings.service.impl.yaml.gitdiff.GitChangeSetProcesser;
import software.wings.service.impl.yaml.sync.GitSyncFailureAlertDetails;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.service.intfc.yaml.sync.GitSyncErrorService;
import software.wings.service.intfc.yaml.sync.GitSyncService;
import software.wings.utils.Utils;
import software.wings.yaml.errorhandling.GitSyncError;
import software.wings.yaml.gitSync.GitFileActivity;
import software.wings.yaml.gitSync.GitWebhookRequestAttributes;
import software.wings.yaml.gitSync.YamlChangeSet;
import software.wings.yaml.gitSync.YamlChangeSet.Status;
import software.wings.yaml.gitSync.YamlGitConfig;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.mongodb.DuplicateKeyException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Transient;
@Slf4j
public class GitCommandCallback implements NotifyCallbackWithErrorHandling {
  private String accountId;
  private String changeSetId;
  private GitCommandType gitCommandType;
  private String gitConnectorId;
  private String repositoryName;
  private String branchName;

  public GitCommandCallback() {}

  public GitCommandCallback(String accountId, String changeSetId, GitCommandType gitCommandType, String gitConnectorId,
      String repositoryName, String branchName) {
    this.accountId = accountId;
    this.changeSetId = changeSetId;
    this.gitCommandType = gitCommandType;
    this.gitConnectorId = gitConnectorId;
    this.repositoryName = repositoryName;
    this.branchName = branchName;
  }

  @Transient @Inject private transient YamlChangeSetService yamlChangeSetService;

  @Transient @Inject private transient YamlGitService yamlGitService;
  @Transient @Inject private transient GitChangeSetProcesser gitChangeSetProcesser;
  @Transient @Inject GitSyncService gitSyncService;
  @Transient @Inject private GitSyncErrorService gitSyncErrorService;
  @Transient @Inject GitChangeSetHandler gitChangeSetHandler;

  public void notify(ResponseData notifyResponseData) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new GitCommandCallbackLogContext(getContext(), OVERRIDE_ERROR)) {
      log.info("Git command response [{}]", notifyResponseData);

      if (notifyResponseData instanceof GitCommandExecutionResponse) {
        GitCommandExecutionResponse gitCommandExecutionResponse = (GitCommandExecutionResponse) notifyResponseData;
        GitCommandResult gitCommandResult = gitCommandExecutionResponse.getGitCommandResult();

        if (gitCommandExecutionResponse.getGitCommandStatus() == GitCommandStatus.FAILURE) {
          if (gitCommandType == COMMIT_AND_PUSH
              && gitCommandExecutionResponse.getErrorCode() == ErrorCode.GIT_UNSEEN_REMOTE_HEAD_COMMIT) {
            handleCommitAndPushFailureWhenUnseenHead(accountId, changeSetId);
            return;
          }

          if (changeSetId != null) {
            log.warn("Git Command failed [{}]", gitCommandExecutionResponse.getErrorMessage());
            yamlChangeSetService.updateStatus(accountId, changeSetId, Status.FAILED);
          }
          if (gitCommandType == DIFF) {
            handleDiffCommandFailure(gitCommandExecutionResponse.getErrorCode(), accountId);
          }
          // raise alert if GitConnectionErrorAlert is not already open

          yamlGitService.raiseAlertForGitFailure(
              accountId, GLOBAL_APP_ID, getGitFailureDetailsFromGitResponse(gitCommandExecutionResponse));

          return;
        }

        // close alert if GitConnectionErrorAlert is open as now connection was successful
        yamlGitService.closeAlertForGitFailureIfOpen(
            accountId, GLOBAL_APP_ID, AlertType.GitConnectionError, createGitConnectionErrorData(accountId));

        log.info("Git command [type: {}] request completed with status [{}]", gitCommandResult.getGitCommandType(),
            gitCommandExecutionResponse.getGitCommandStatus());

        if (gitCommandResult.getGitCommandType() == COMMIT_AND_PUSH) {
          GitCommitAndPushResult gitCommitAndPushResult = (GitCommitAndPushResult) gitCommandResult;
          YamlChangeSet yamlChangeSet = yamlChangeSetService.get(accountId, changeSetId);
          List<GitFileChange> filesCommited = Collections.emptyList();
          if (yamlChangeSet != null) {
            yamlChangeSetService.updateStatus(accountId, changeSetId, Status.COMPLETED);
            if (gitCommitAndPushResult.getGitCommitResult().getCommitId() != null) {
              List<String> yamlSetIdsProcessed =
                  ((GitCommitRequest) gitCommandExecutionResponse.getGitCommandRequest()).getYamlChangeSetIds();
              List<String> yamlGitConfigIds =
                  yamlGitService.getYamlGitConfigIds(accountId, gitConnectorId, branchName, repositoryName);

              saveCommitFromHarness(gitCommitAndPushResult, yamlChangeSet, yamlGitConfigIds, yamlSetIdsProcessed);
              final String processingCommitId = gitCommitAndPushResult.getGitCommitResult().getCommitId();
              final String processingCommitMessage = gitCommitAndPushResult.getGitCommitResult().getCommitMessage();
              filesCommited = emptyIfNull(gitCommitAndPushResult.getFilesCommitedToGit());
              addYamlChangeSetToFilesCommited(filesCommited, gitCommitAndPushResult.getYamlGitConfig());
              gitSyncService.logActivityForGitOperation(filesCommited, GitFileActivity.Status.SUCCESS, false,
                  yamlChangeSet.isFullSync(), "", processingCommitId, processingCommitMessage);
              gitSyncService.createGitFileActivitySummaryForCommit(
                  processingCommitId, accountId, false, GitCommit.Status.COMPLETED);
            }
            yamlGitService.removeGitSyncErrors(
                accountId, getAllFilesSuccessFullyProccessed(yamlChangeSet.getGitFileChanges(), filesCommited), false);
          }
        } else if (gitCommandResult.getGitCommandType() == DIFF) {
          try {
            GitDiffResult gitDiffResult = (GitDiffResult) gitCommandResult;

            if (isNotEmpty(gitDiffResult.getGitFileChanges())) {
              addActiveGitSyncErrorsToProcessAgain(gitDiffResult, accountId);
            } else {
              log.info("No file changes found in git diff. Skip adding active errors for processing");
            }
            gitChangeSetProcesser.processGitChangeSet(accountId, gitDiffResult);
            yamlChangeSetService.updateStatus(accountId, changeSetId, Status.COMPLETED);
          } catch (Exception e) {
            log.error("error while processing diff request", e);
            yamlChangeSetService.updateStatus(accountId, changeSetId, Status.FAILED);
            handleDiffCommandFailure(null, accountId);
          }
        } else {
          log.warn("Unexpected commandType result: [{}]", gitCommandExecutionResponse.getErrorMessage());
          yamlChangeSetService.updateStatus(accountId, changeSetId, Status.FAILED);
        }
      } else {
        log.warn("Unexpected notify response data: [{}]", notifyResponseData);
        updateChangeSetFailureStatusSafely();
      }
    }
  }

  private void handleCommitAndPushFailureWhenUnseenHead(String accountId, String changeSetId) {
    log.info("Remote head commit did not match local head commit. Requeuing changeset [{}] again.", changeSetId);
    if (changeSetId != null) {
      yamlChangeSetService.updateStatusAndIncrementPushCount(accountId, changeSetId, Status.QUEUED);
    }
  }

  private void addYamlChangeSetToFilesCommited(List<GitFileChange> gitFileChanges, YamlGitConfig yamlGitConfig) {
    if (isEmpty(gitFileChanges)) {
      return;
    }
    gitFileChanges.forEach(gitFileChange -> gitFileChange.setYamlGitConfig(yamlGitConfig));
  }

  private GitConnectionErrorAlert createGitConnectionErrorData(String accountId) {
    return GitConnectionErrorAlert.builder()
        .accountId(accountId)
        .gitConnectorId(gitConnectorId)
        .branchName(branchName)
        .repositoryName(repositoryName)
        .build();
  }

  private GitSyncFailureAlertDetails getGitFailureDetailsFromGitResponse(
      GitCommandExecutionResponse gitCommandExecutionResponse) {
    return GitSyncFailureAlertDetails.builder()
        .errorCode(gitCommandExecutionResponse.getErrorCode())
        .errorMessage(gitCommandExecutionResponse.getErrorMessage())
        .gitConnectorId(gitConnectorId)
        .branchName(branchName)
        .repositoryName(repositoryName)
        .build();
  }

  private List<GitFileChange> getActiveGitSyncErrorFiles(
      String accountId, String branchName, String repositoryName, String gitConnectorId) {
    final long _30_days_millis = System.currentTimeMillis() - Duration.ofDays(30).toMillis();
    return gitSyncErrorService
        .getActiveGitToHarnessSyncErrors(accountId, gitConnectorId, branchName, repositoryName, _30_days_millis)
        .stream()
        .map(this::convertToGitFileChange)
        .collect(Collectors.toList());
  }

  private GitFileChange convertToGitFileChange(GitSyncError gitSyncError) {
    return aGitFileChange()
        .withFilePath(gitSyncError.getYamlFilePath())
        .withFileContent(getYamlContentOfError(gitSyncError))
        .withAccountId(gitSyncError.getAccountId())
        .withChangeType(Utils.getEnumFromString(ChangeType.class, gitSyncError.getChangeType()))
        .withSyncFromGit(true)
        .withCommitId(getCommitIdOfError(gitSyncError))
        .withChangeFromAnotherCommit(Boolean.TRUE)
        .withCommitTimeMs(getCommitTimeOfError(gitSyncError))
        .withCommitMessage(getCommitMessageOfError(gitSyncError))
        .build();
  }
  @VisibleForTesting
  void addActiveGitSyncErrorsToProcessAgain(final GitDiffResult gitDiffResult, final String accountId) {
    final List<GitFileChange> activeGitSyncErrorFiles = emptyIfNull(getActiveGitSyncErrorFiles(accountId,
        gitDiffResult.getYamlGitConfig().getBranchName(), gitDiffResult.getYamlGitConfig().getRepositoryName(),
        gitDiffResult.getYamlGitConfig().getGitConnectorId()));

    log.info("Active git sync error files =[{}]",
        activeGitSyncErrorFiles.stream().map(GitFileChange::getFilePath).collect(Collectors.toList()));

    if (isNotEmpty(activeGitSyncErrorFiles)) {
      final Set<String> filesAlreadyInDiffSet =
          gitDiffResult.getGitFileChanges().stream().map(GitFileChange::getFilePath).collect(Collectors.toSet());

      final List<GitFileChange> activeErrorsNotInDiff =
          activeGitSyncErrorFiles.stream()
              .filter(gitFileChange -> !filesAlreadyInDiffSet.contains(gitFileChange.getFilePath()))
              .collect(Collectors.toList());

      log.info("Active git sync error files not in diff =[{}]",
          activeErrorsNotInDiff.stream().map(GitFileChange::getFilePath).collect(Collectors.toList()));

      activeErrorsNotInDiff.forEach(gitDiffResult::addChangeFile);
    }
  }

  private void saveCommitFromHarness(GitCommitAndPushResult gitCommitAndPushResult, YamlChangeSet yamlChangeSet,
      List<String> yamlGitConfigIds, List<String> yamlSetIdsProcessed) {
    String commitId = gitCommitAndPushResult.getGitCommitResult().getCommitId();
    YamlGitConfig yamlGitConfig = gitCommitAndPushResult.getYamlGitConfig();
    if (yamlGitConfig == null) {
      throw new UnexpectedException(String.format(
          "Error while saving commit for commitId=[%s],yamlChangeSetId=[%s] as the yamlGitConfig is null ", commitId,
          yamlChangeSet.getUuid()));
    }
    saveGitCommit(GitCommit.builder()
                      .accountId(accountId)
                      .yamlChangeSet(yamlChangeSet)
                      .yamlGitConfigIds(yamlGitConfigIds)
                      .status(GitCommit.Status.COMPLETED)
                      .commitId(commitId)
                      .gitCommandResult(gitCommitAndPushResult)
                      .gitConnectorId(yamlGitConfig.getGitConnectorId())
                      .repositoryName(yamlGitConfig.getRepositoryName())
                      .branchName(yamlGitConfig.getBranchName())
                      .yamlChangeSetsProcessed(yamlSetIdsProcessed)
                      .commitMessage(gitCommitAndPushResult.getGitCommitResult().getCommitMessage())
                      .build());
  }

  private GitCommit saveGitCommit(GitCommit gitCommit) {
    GitCommit gitCommitSaved = null;
    try {
      gitCommitSaved = yamlGitService.saveCommit(gitCommit);
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

        gitCommitSaved = yamlGitService.saveCommit(gitCommit);
      }
    }
    return gitCommitSaved;
  }

  protected void updateChangeSetFailureStatusSafely() {
    if (isNotEmpty(changeSetId) && (COMMIT_AND_PUSH == gitCommandType || DIFF == gitCommandType)) {
      yamlChangeSetService.updateStatus(accountId, changeSetId, Status.FAILED);
    }
  }

  protected void updateGitCommitFailureSafely() {
    if (DIFF == gitCommandType) {
      handleDiffCommandFailure(null, accountId);
    }
  }

  private ImmutableMap<String, String> getContext() {
    final ImmutableMap.Builder<String, String> context = ImmutableMap.builder();
    context.put("gitCommandCallBackType", gitCommandType.toString());

    if (isNotBlank(changeSetId)) {
      context.put(CHANGESET_ID, changeSetId);
    }

    return context.build();
  }

  private void handleDiffCommandFailure(ErrorCode errorCode, String accountId) {
    if (isNotEmpty(changeSetId)) {
      final YamlChangeSet yamlChangeSet = yamlChangeSetService.get(accountId, changeSetId);
      if (yamlChangeSet == null) {
        log.error("no changeset found with id =[{}]", changeSetId);
        return;
      }
      final GitWebhookRequestAttributes gitWebhookRequestAttributes = yamlChangeSet.getGitWebhookRequestAttributes();
      if (isValid(gitWebhookRequestAttributes)) {
        final String headCommitId = gitWebhookRequestAttributes.getHeadCommitId();
        String repositoryFullName = yamlChangeSet.getGitWebhookRequestAttributes().getRepositoryFullName();
        final List<String> yamlConfigIds =
            yamlGitService.getYamlGitConfigIds(accountId, gitConnectorId, branchName, repositoryFullName);

        GitCommit.Status gitCommitStatus = GitCommit.Status.FAILED;
        if (ErrorCode.GIT_DIFF_COMMIT_NOT_IN_ORDER == errorCode) {
          gitCommitStatus = GitCommit.Status.SKIPPED;
        }
        GitCommit gitCommit = saveFailedCommitFromGit(
            headCommitId, yamlConfigIds, accountId, gitCommitStatus, gitConnectorId, repositoryName, branchName);
        gitSyncService.createGitFileSummaryForFailedOrSkippedCommit(gitCommit, true);
      }
    }
  }

  private boolean isValid(GitWebhookRequestAttributes gitWebhookRequestAttributes) {
    return gitWebhookRequestAttributes != null && isNotEmpty(gitWebhookRequestAttributes.getHeadCommitId())
        && isNotEmpty(gitWebhookRequestAttributes.getBranchName())
        && isNotEmpty(gitWebhookRequestAttributes.getGitConnectorId());
  }
  private GitCommit saveFailedCommitFromGit(String commitId, List<String> yamlGitConfigIds, String accountId,
      GitCommit.Status gitCommitStatus, String gitConnectorId, String repositoryName, String branchName) {
    return saveGitCommit(GitCommit.builder()
                             .accountId(accountId)
                             .yamlChangeSet(YamlChangeSet.builder()
                                                .accountId(accountId)
                                                .appId(GLOBAL_APP_ID)
                                                .gitToHarness(true)
                                                .status(Status.COMPLETED)
                                                .gitFileChanges(null)
                                                .build())
                             .yamlGitConfigIds(yamlGitConfigIds)
                             .status(gitCommitStatus)
                             .commitId(commitId)
                             .gitConnectorId(gitConnectorId)
                             .repositoryName(repositoryName)
                             .branchName(branchName)
                             .gitCommandResult(null)
                             .fileProcessingSummary(null)
                             .build());
  }

  @VisibleForTesting
  List<GitFileChange> getAllFilesSuccessFullyProccessed(
      List<GitFileChange> fileChangesPartOfYamlChangeSet, List<GitFileChange> filesCommited) {
    List<GitFileChange> allFilesProcessed = new ArrayList<>(fileChangesPartOfYamlChangeSet);
    if (isEmpty(filesCommited)) {
      return allFilesProcessed;
    }
    Set<String> nameOfFilesProcessed =
        fileChangesPartOfYamlChangeSet.stream().map(change -> change.getFilePath()).collect(Collectors.toSet());
    filesCommited.forEach(change -> {
      if (!nameOfFilesProcessed.contains(change.getFilePath())) {
        allFilesProcessed.add(change);
      }
    });
    return allFilesProcessed;
  }

  @Override
  public void notify(Map<String, Supplier<ResponseData>> response) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new GitCommandCallbackLogContext(getContext(), OVERRIDE_ERROR)) {
      Supplier<ResponseData> responseDataSupplier = response.values().iterator().next();
      try {
        ResponseData responseData = responseDataSupplier.get();
        notify(responseData);
      } catch (NoAvailableDelegatesException | NoInstalledDelegatesException e) {
        log.error("Git request failed for command:[{}], changeSetId:[{}], account:[{}], response:[{}]", gitCommandType,
            changeSetId, accountId, response);
        log.error("Delegate not available or installed, retrying.", e);
        yamlChangeSetService.updateStatusAndIncrementRetryCountForYamlChangeSets(accountId, Status.QUEUED,
            Collections.singletonList(Status.RUNNING), Collections.singletonList(changeSetId));
      } catch (Exception e) {
        log.error("Git request failed for command:[{}], changeSetId:[{}], account:[{}], response:[{}]", gitCommandType,
            changeSetId, accountId, response);
        log.error("Failure in git command execution", e);
        updateChangeSetFailureStatusSafely();
        updateGitCommitFailureSafely();
      }
    }
  }
}
