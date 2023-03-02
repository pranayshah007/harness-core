/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.git.model.FetchFilesResult;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.SshSessionConfig;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@OwnedBy(CDP)
public class GitFetchTaskNG extends AbstractDelegateRunnableTask {
  @Inject private NGGitService ngGitService;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private GitFetchFilesTaskHelper gitFetchFilesTaskHelper;
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private ScmFetchFilesHelperNG scmFetchFilesHelper;

  public static final int GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT = 10;

  public GitFetchTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public GitFetchResponse run(TaskParameters parameters) {
    GitFetchRequest gitFetchRequest = (GitFetchRequest) parameters;
    CommandUnitsProgress commandUnitsProgress = gitFetchRequest.getCommandUnitsProgress() != null
        ? gitFetchRequest.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();
    try {
      log.info("Running GitFetchFilesTask for activityId {}", gitFetchRequest.getActivityId());

      LogCallback executionLogCallback = new NGDelegateLogCallback(getLogStreamingTaskClient(),
          K8sCommandUnitConstants.FetchFiles, gitFetchRequest.isShouldOpenLogStream(), commandUnitsProgress);

      Map<String, FetchFilesResult> filesFromMultipleRepo = new HashMap<>();
      List<GitFetchFilesConfig> gitFetchFilesConfigs = gitFetchRequest.getGitFetchFilesConfigs();

      executionLogCallback.saveExecutionLog(
          color(format("%nStarting Git Fetch Files"), LogColor.White, LogWeight.Bold));

      for (GitFetchFilesConfig gitFetchFilesConfig : gitFetchFilesConfigs) {
        FetchFilesResult gitFetchFilesResult;
        if (gitFetchFilesConfig.isSucceedIfFileNotFound()) {
          executionLogCallback.saveExecutionLog(
              format("\nTrying to fetch default values yaml file for manifest with identifier: [%s]",
                  gitFetchFilesConfig.getIdentifier()));
        }
        executionLogCallback.saveExecutionLog(
            color(format("Fetching %s files with identifier: %s", gitFetchFilesConfig.getManifestType(),
                      gitFetchFilesConfig.getIdentifier()),
                White, Bold));

        try {
          gitFetchFilesResult = fetchFilesFromRepo(gitFetchFilesConfig, executionLogCallback,
              gitFetchRequest.getAccountId(), gitFetchRequest.isCloseLogStream());
        } catch (Exception ex) {
          String exceptionMsg = gitFetchFilesTaskHelper.extractErrorMessage(ex);

          // Values.yaml in service spec is optional.
          if (ex.getCause() instanceof NoSuchFileException && gitFetchFilesConfig.isSucceedIfFileNotFound()) {
            log.info("file not found. " + exceptionMsg, ex);
            executionLogCallback.saveExecutionLog(color(
                format("No values.yaml found for manifest with identifier: %s.", gitFetchFilesConfig.getIdentifier()),
                White));
            continue;
          }

          String msg = "Exception in processing GitFetchFilesTask. " + exceptionMsg;
          log.error(msg, ex);
          executionLogCallback.saveExecutionLog(msg, ERROR, CommandExecutionStatus.FAILURE);
          throw ex;
        }

        if (filesFromMultipleRepo.containsKey(gitFetchFilesConfig.getIdentifier())) {
          FetchFilesResult fetchFilesResult = filesFromMultipleRepo.get(gitFetchFilesConfig.getIdentifier());
          if (fetchFilesResult.getFiles() != null && gitFetchFilesResult.getFiles() != null) {
            fetchFilesResult.getFiles().addAll(gitFetchFilesResult.getFiles());
            filesFromMultipleRepo.put(gitFetchFilesConfig.getIdentifier(), fetchFilesResult);
          }
        } else {
          filesFromMultipleRepo.put(gitFetchFilesConfig.getIdentifier(), gitFetchFilesResult);
        }
      }
      executionLogCallback.saveExecutionLog(
          color(format("%nGit Fetch Files completed successfully."), LogColor.White, LogWeight.Bold), INFO);

      if (gitFetchRequest.isCloseLogStream()) {
        executionLogCallback.saveExecutionLog("Done.", INFO, CommandExecutionStatus.SUCCESS);
      }
      return GitFetchResponse.builder()
          .taskStatus(TaskStatus.SUCCESS)
          .filesFromMultipleRepo(filesFromMultipleRepo)
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    } catch (Exception exception) {
      log.error("Exception in Git Fetch Files Task", exception);
      throw new TaskNGDataException(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), exception);
    }
  }

  private FetchFilesResult fetchFilesFromRepo(GitFetchFilesConfig gitFetchFilesConfig, LogCallback executionLogCallback,
      String accountId, boolean closeLogStream) throws IOException {
    GitStoreDelegateConfig gitStoreDelegateConfig = gitFetchFilesConfig.getGitStoreDelegateConfig();
    executionLogCallback.saveExecutionLog("Git connector Url: " + gitStoreDelegateConfig.getGitConfigDTO().getUrl());
    String fetchTypeInfo = gitStoreDelegateConfig.getFetchType() == FetchType.BRANCH
        ? "Branch: " + gitStoreDelegateConfig.getBranch()
        : "CommitId: " + gitStoreDelegateConfig.getCommitId();

    executionLogCallback.saveExecutionLog(fetchTypeInfo);

    List<String> filePathsToFetch = null;
    if (EmptyPredicate.isNotEmpty(gitFetchFilesConfig.getGitStoreDelegateConfig().getPaths())) {
      filePathsToFetch = gitFetchFilesConfig.getGitStoreDelegateConfig().getPaths();
      executionLogCallback.saveExecutionLog("\nFetching following Files :");
      gitFetchFilesTaskHelper.printFileNamesInExecutionLogs(filePathsToFetch, executionLogCallback, closeLogStream);
    }

    FetchFilesResult gitFetchFilesResult;
    if (gitStoreDelegateConfig.isOptimizedFilesFetch()) {
      executionLogCallback.saveExecutionLog("Using optimized file fetch");
      secretDecryptionService.decrypt(
          GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(gitStoreDelegateConfig.getGitConfigDTO()),
          gitStoreDelegateConfig.getApiAuthEncryptedDataDetails());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(gitStoreDelegateConfig.getGitConfigDTO()),
          gitStoreDelegateConfig.getApiAuthEncryptedDataDetails());
      gitFetchFilesResult = scmFetchFilesHelper.fetchFilesFromRepoWithScm(gitStoreDelegateConfig, filePathsToFetch);
    } else {
      GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO());
      gitDecryptionHelper.decryptGitConfig(gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
      SshSessionConfig sshSessionConfig = gitDecryptionHelper.getSSHSessionConfig(
          gitStoreDelegateConfig.getSshKeySpecDTO(), gitStoreDelegateConfig.getEncryptedDataDetails());
      gitFetchFilesResult =
          ngGitService.fetchFilesByPath(gitStoreDelegateConfig, accountId, sshSessionConfig, gitConfigDTO);
    }

    gitFetchFilesTaskHelper.printFileNamesInExecutionLogs(executionLogCallback, gitFetchFilesResult.getFiles(), false);

    return gitFetchFilesResult;
  }

  @Override
  public GitFetchResponse run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
