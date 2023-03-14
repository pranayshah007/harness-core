/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.govern.Switch.unhandled;
import static io.harness.k8s.K8sCommandUnitConstants.FetchFiles;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.git.GitFetchFilesTaskHelper;
import io.harness.git.GitFetchMetadataContext;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.ScmServiceClient;

import software.wings.beans.GitConfig;
import software.wings.beans.GitFetchFilesConfig;
import software.wings.beans.GitFetchFilesTaskParams;
import software.wings.beans.GitFileConfig;
import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitCommandExecutionResponse.GitCommandStatus;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.service.intfc.GitService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class GitFetchFilesTask extends AbstractDelegateRunnableTask {
  @Inject private GitService gitService;
  @Inject private EncryptionService encryptionService;
  @Inject private DelegateLogService delegateLogService;
  @Inject private GitFetchFilesTaskHelper gitFetchFilesTaskHelper;
  @Inject private ScmDelegateClient scmDelegateClient;
  @Inject private ScmServiceClient scmServiceClient;
  @Inject private ScmFetchFilesHelper scmFetchFilesHelper;

  public static final int GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT = 10;

  public GitFetchFilesTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public GitCommandExecutionResponse run(TaskParameters parameters) {
    GitFetchFilesTaskParams taskParams = (GitFetchFilesTaskParams) parameters;
    log.info("Running GitFetchFilesTask for account {}, app {}, activityId {}", taskParams.getAccountId(),
        taskParams.getAppId(), taskParams.getActivityId());

    String executionLogName = isEmpty(taskParams.getExecutionLogName()) ? FetchFiles : taskParams.getExecutionLogName();

    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback(delegateLogService, taskParams.getAccountId(),
        taskParams.getAppId(), taskParams.getActivityId(), executionLogName);

    AppManifestKind appManifestKind = taskParams.getAppManifestKind();
    Map<String, GitFetchFilesResult> filesFromMultipleRepo = new HashMap<>();
    Map<String, GitFetchFilesConfig> gitFetchFilesConfigMap = taskParams.getGitFetchFilesConfigMap();

    try (GitFetchMetadataContext fetchMetadataContext = new GitFetchMetadataContext()) {
      executionLogCallback.saveExecutionLog(color(
          format("%nStarting Git %s Fetch", getFileTypeMessage(appManifestKind)), LogColor.White, LogWeight.Bold));

      for (Entry<String, GitFetchFilesConfig> entry : taskParams.getGitFetchFilesConfigMap().entrySet()) {
        executionLogCallback.saveExecutionLog(
            color(format("%nFetching %s files from git for %s", getFileTypeMessage(appManifestKind), entry.getKey()),
                LogColor.White, LogWeight.Bold));

        GitFetchFilesConfig gitFetchFileConfig = entry.getValue();
        String k8ValuesLocation = entry.getKey();
        GitFetchFilesResult gitFetchFilesResult;

        try {
          gitFetchFilesResult = fetchFilesFromRepo(k8ValuesLocation, gitFetchFileConfig.getGitFileConfig(),
              gitFetchFileConfig.getGitConfig(), gitFetchFileConfig.getEncryptedDataDetails(), executionLogCallback,
              taskParams.isOptimizedFilesFetch(), taskParams.isCloseLogStream());
        } catch (Exception ex) {
          String exceptionMsg = gitFetchFilesTaskHelper.extractErrorMessage(ex);

          // Values.yaml in service spec is optional.
          if (AppManifestKind.VALUES == appManifestKind && K8sValuesLocation.Service.toString().equals(k8ValuesLocation)
              && ex.getCause() instanceof NoSuchFileException) {
            log.info("Values.yaml file not found. " + exceptionMsg, ex);
            executionLogCallback.saveExecutionLog(exceptionMsg, WARN);
            if (taskParams.isShouldInheritGitFetchFilesConfigMap()
                && entry.getValue().getGitFileConfig().isUseBranch()) {
              executionLogCallback.saveExecutionLog(color(
                  "Unable to resolve git reference for values yaml, future phases will continue to use branch for fetching",
                  Yellow, Bold));
            }
            continue;
          }

          String msg = "Exception in processing GitFetchFilesTask. " + exceptionMsg;
          log.error(msg, ex);
          executionLogCallback.saveExecutionLog(msg, ERROR, CommandExecutionStatus.FAILURE);
          return GitCommandExecutionResponse.builder()
              .fetchedCommitIdsMap(new HashMap<>(fetchMetadataContext.getCommitIdMap()))
              .errorMessage(exceptionMsg)
              .gitCommandStatus(GitCommandStatus.FAILURE)
              .build();
        }

        filesFromMultipleRepo.put(entry.getKey(), gitFetchFilesResult);

        if (taskParams.isShouldInheritGitFetchFilesConfigMap() && entry.getValue().getGitFileConfig().isUseBranch()) {
          gitFetchFilesConfigMap.get(entry.getKey()).getGitFileConfig().setUseBranch(false);
          gitFetchFilesConfigMap.get(entry.getKey())
              .getGitFileConfig()
              .setCommitId(gitFetchFilesResult.getLatestCommitSHA());

          executionLogCallback.saveExecutionLog(color(
              String.format(
                  "Recorded Latest CommitId: %s and will use this Commit Id to fetch Values Yaml from git throughout this workflow",
                  gitFetchFilesResult.getLatestCommitSHA()),
              White, Bold));
        }
      }

      if (taskParams.isFinalState()) {
        executionLogCallback.saveExecutionLog(
            color(format("%nGit %s Fetch completed successfully.", getFileTypeMessage(appManifestKind)), LogColor.White,
                LogWeight.Bold),
            INFO, CommandExecutionStatus.SUCCESS);
      }
      return GitCommandExecutionResponse.builder()
          .gitCommandResult(GitFetchFilesFromMultipleRepoResult.builder()
                                .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
                                .filesFromMultipleRepo(filesFromMultipleRepo)
                                .build())
          .fetchedCommitIdsMap(new HashMap<>(fetchMetadataContext.getCommitIdMap()))
          .gitCommandStatus(GitCommandStatus.SUCCESS)
          .build();
    } catch (Exception ex) {
      String exceptionMsg = gitFetchFilesTaskHelper.extractErrorMessage(ex);
      String msg = "Exception in processing GitFetchFilesTask. " + ex;
      log.error(msg, ex);
      executionLogCallback.saveExecutionLog(msg, ERROR, CommandExecutionStatus.FAILURE);
      return GitCommandExecutionResponse.builder()
          .errorMessage(exceptionMsg)
          .gitCommandStatus(GitCommandStatus.FAILURE)
          .build();
    }
  }

  private GitFetchFilesResult fetchFilesFromRepo(String identifier, GitFileConfig gitFileConfig, GitConfig gitConfig,
      List<EncryptedDataDetail> encryptedDataDetails, ExecutionLogCallback executionLogCallback,
      boolean optimizedFilesFetch, boolean closeLogStream) {
    executionLogCallback.saveExecutionLog("Git connector Url: " + gitConfig.getRepoUrl());
    if (gitFileConfig.isUseBranch()) {
      executionLogCallback.saveExecutionLog("Branch: " + gitFileConfig.getBranch());
    } else {
      executionLogCallback.saveExecutionLog("CommitId: " + gitFileConfig.getCommitId());
    }

    List<String> filePathsToFetch = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(gitFileConfig.getTaskSpecFilePath())
        || EmptyPredicate.isNotEmpty(gitFileConfig.getServiceSpecFilePath())) {
      filePathsToFetch.add(gitFileConfig.getTaskSpecFilePath());
      if (!gitFileConfig.isUseInlineServiceDefinition()) {
        filePathsToFetch.add(gitFileConfig.getServiceSpecFilePath());
      }
      executionLogCallback.saveExecutionLog("\nFetching following Task and Service Spec files :");
      gitFetchFilesTaskHelper.printFileNamesInExecutionLogs(filePathsToFetch, executionLogCallback, true);
    } else if (EmptyPredicate.isNotEmpty(gitFileConfig.getFilePathList())) {
      filePathsToFetch = gitFileConfig.getFilePathList();
      executionLogCallback.saveExecutionLog("\nFetching following Files :");
      gitFetchFilesTaskHelper.printFileNamesInExecutionLogs(filePathsToFetch, executionLogCallback, true);
    } else {
      executionLogCallback.saveExecutionLog("\nFetching " + gitFileConfig.getFilePath());
      String filePath = isBlank(gitFileConfig.getFilePath()) ? "" : gitFileConfig.getFilePath();
      filePathsToFetch = Collections.singletonList(filePath);
    }

    GitFetchFilesResult gitFetchFilesResult;
    encryptionService.decrypt(gitConfig, encryptedDataDetails, false);
    if (scmFetchFilesHelper.shouldUseScm(optimizedFilesFetch, gitConfig)) {
      gitFetchFilesResult =
          scmFetchFilesHelper.fetchFilesFromRepoWithScm(identifier, gitFileConfig, gitConfig, filePathsToFetch);
    } else {
      gitFetchFilesResult = gitService.fetchFilesByPath(identifier, gitConfig, gitFileConfig.getConnectorId(),
          gitFileConfig.getCommitId(), gitFileConfig.getBranch(), filePathsToFetch, gitFileConfig.isUseBranch(), true,
          executionLogCallback);
      if (gitFileConfig.isUseBranch()) {
        executionLogCallback.saveExecutionLog("\nFetched files for Branch: " + gitFileConfig.getBranch()
            + " with CommitId: " + gitFetchFilesResult.getLatestCommitSHA());
      }
    }

    gitFetchFilesTaskHelper.printFileNamesInExecutionLogs(executionLogCallback,
        gitFetchFilesResult == null ? Collections.emptyList() : gitFetchFilesResult.getFiles(), true);

    return gitFetchFilesResult;
  }

  @Override
  public GitCommandExecutionResponse run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  private String getFileTypeMessage(AppManifestKind appManifestKind) {
    if (appManifestKind == null) {
      return "";
    }

    switch (appManifestKind) {
      case VALUES:
        return "Values";
      case PCF_OVERRIDE:
      case K8S_MANIFEST:
        return "manifest";

      default:
        unhandled(appManifestKind);
        return "";
    }
  }
}
