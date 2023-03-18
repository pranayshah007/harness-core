/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.NO_SERVERLESS_MANIFEST_EXPLANATION;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.NO_SERVERLESS_MANIFEST_FAILED;
import static io.harness.delegate.task.serverless.exception.ServerlessExceptionConstants.NO_SERVERLESS_MANIFEST_HINT;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Red;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
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
import io.harness.delegate.task.git.GitFetchTaskHelper;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.serverless.request.ServerlessGitFetchRequest;
import io.harness.delegate.task.serverless.response.ServerlessGitFetchResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.serverless.ServerlessCommandExecutionException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.git.model.FetchFilesResult;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.secret.SecretSanitizerThreadLocal;
import io.harness.serverless.ServerlessCommandUnitConstants;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class ServerlessGitFetchTask extends AbstractDelegateRunnableTask {
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private GitFetchTaskHelper gitFetchTaskHelper;
  public ServerlessGitFetchTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }
  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    ServerlessGitFetchRequest serverlessGitFetchRequest = (ServerlessGitFetchRequest) parameters;
    LogCallback executionLogCallback =
        new NGDelegateLogCallback(getLogStreamingTaskClient(), ServerlessCommandUnitConstants.fetchFiles.toString(),
            serverlessGitFetchRequest.isShouldOpenLogStream(), commandUnitsProgress);
    try {
      log.info("Running Serverless GitFetchFilesTask for activityId {}", serverlessGitFetchRequest.getActivityId());

      ServerlessGitFetchFileConfig serverlessGitFetchFileConfig =
          serverlessGitFetchRequest.getServerlessGitFetchFileConfig();
      executionLogCallback.saveExecutionLog(
          color(format("Fetching %s config file with identifier: %s", serverlessGitFetchFileConfig.getManifestType(),
                    serverlessGitFetchFileConfig.getIdentifier()),
              White, Bold));
      Map<String, FetchFilesResult> filesFromMultipleRepo = new HashMap<>();
      FetchFilesResult filesResult = fetchManifestFile(serverlessGitFetchFileConfig, executionLogCallback,
          serverlessGitFetchRequest.getAccountId(), serverlessGitFetchRequest.isCloseLogStream());
      filesFromMultipleRepo.put(serverlessGitFetchFileConfig.getIdentifier(), filesResult);
      executionLogCallback.saveExecutionLog(
          color(format("%nFetch Config File completed successfully..%n"), LogColor.White, LogWeight.Bold), INFO);
      executionLogCallback.saveExecutionLog("Done..\n", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      return ServerlessGitFetchResponse.builder()
          .taskStatus(TaskStatus.SUCCESS)
          .filesFromMultipleRepo(filesFromMultipleRepo)
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();

    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in Git Fetch Files Task", sanitizedException);
      executionLogCallback.saveExecutionLog(
          color(format("%n File fetch failed with error: %s", ExceptionUtils.getMessage(sanitizedException)),
              LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    }
  }

  private FetchFilesResult fetchManifestFile(ServerlessGitFetchFileConfig serverlessGitFetchFileConfig,
      LogCallback executionLogCallback, String accountId, boolean closeLogStream) throws Exception {
    GitStoreDelegateConfig gitStoreDelegateConfig = serverlessGitFetchFileConfig.getGitStoreDelegateConfig();
    executionLogCallback.saveExecutionLog("Git connector Url: " + gitStoreDelegateConfig.getGitConfigDTO().getUrl());
    String fetchTypeInfo;
    GitConfigDTO gitConfigDTO = null;
    if (gitStoreDelegateConfig.getFetchType() == FetchType.BRANCH) {
      fetchTypeInfo = "Branch: " + gitStoreDelegateConfig.getBranch();
    } else {
      fetchTypeInfo = "Commit: " + gitStoreDelegateConfig.getCommitId();
    }
    executionLogCallback.saveExecutionLog(fetchTypeInfo);
    if (gitStoreDelegateConfig.isOptimizedFilesFetch()) {
      executionLogCallback.saveExecutionLog("Using optimized file fetch ");
      gitFetchTaskHelper.decryptGitStoreConfig(gitStoreDelegateConfig);
    } else {
      gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO());
      gitDecryptionHelper.decryptGitConfig(gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
    }
    FetchFilesResult filesResult = null;
    try {
      if (EmptyPredicate.isNotEmpty(gitStoreDelegateConfig.getPaths())) {
        String folderPath = serverlessGitFetchFileConfig.getGitStoreDelegateConfig().getPaths().get(0);
        if (EmptyPredicate.isNotEmpty(serverlessGitFetchFileConfig.getConfigOverridePath())) {
          filesResult = fetchManifestFileFromRepo(gitStoreDelegateConfig, folderPath,
              serverlessGitFetchFileConfig.getConfigOverridePath(), accountId, gitConfigDTO, executionLogCallback,
              closeLogStream);
        } else {
          filesResult = fetchManifestFileInPriorityOrder(
              gitStoreDelegateConfig, folderPath, accountId, gitConfigDTO, executionLogCallback, closeLogStream);
        }
      }
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      String msg = "Exception in processing GitFetchFilesTask. " + sanitizedException.getMessage();
      if (sanitizedException.getCause() instanceof NoSuchFileException) {
        log.error(msg, sanitizedException);
        executionLogCallback.saveExecutionLog(
            color(format("No manifest file found with identifier: %s.", serverlessGitFetchFileConfig.getIdentifier()),
                Red),
            ERROR);
      }
      throw sanitizedException;
    }
    return filesResult;
  }

  private FetchFilesResult fetchManifestFileInPriorityOrder(GitStoreDelegateConfig gitStoreDelegateConfig,
      String folderPath, String accountId, GitConfigDTO gitConfigDTO, LogCallback executionLogCallback,
      boolean closeLogStream) {
    // todo: // optimize in such a way fetching of files from git happens only once
    Optional<FetchFilesResult> serverlessManifestFileResult;
    serverlessManifestFileResult = fetchServerlessManifestFileFromRepo(gitStoreDelegateConfig, folderPath,
        "serverless.yaml", accountId, gitConfigDTO, executionLogCallback, closeLogStream);
    if (serverlessManifestFileResult.isPresent()) {
      return serverlessManifestFileResult.get();
    }
    serverlessManifestFileResult = fetchServerlessManifestFileFromRepo(gitStoreDelegateConfig, folderPath,
        "serverless.yml", accountId, gitConfigDTO, executionLogCallback, closeLogStream);
    if (serverlessManifestFileResult.isPresent()) {
      return serverlessManifestFileResult.get();
    }
    serverlessManifestFileResult = fetchServerlessManifestFileFromRepo(gitStoreDelegateConfig, folderPath,
        "serverless.json", accountId, gitConfigDTO, executionLogCallback, closeLogStream);
    if (serverlessManifestFileResult.isPresent()) {
      return serverlessManifestFileResult.get();
    }
    executionLogCallback.saveExecutionLog(
        color(format("No manifest file found with identifier: %s.", gitStoreDelegateConfig.getManifestId()), Red),
        ERROR);
    throw NestedExceptionUtils.hintWithExplanationException(format(NO_SERVERLESS_MANIFEST_HINT, folderPath),
        format(NO_SERVERLESS_MANIFEST_EXPLANATION, folderPath),
        new ServerlessCommandExecutionException(NO_SERVERLESS_MANIFEST_FAILED));
  }

  private Optional<FetchFilesResult> fetchServerlessManifestFileFromRepo(GitStoreDelegateConfig gitStoreDelegateConfig,
      String folderPath, String filePath, String accountId, GitConfigDTO gitConfigDTO, LogCallback executionLogCallback,
      boolean closeLogStream) {
    try {
      return Optional.of(fetchManifestFileFromRepo(
          gitStoreDelegateConfig, folderPath, filePath, accountId, gitConfigDTO, executionLogCallback, closeLogStream));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private FetchFilesResult fetchManifestFileFromRepo(GitStoreDelegateConfig gitStoreDelegateConfig, String folderPath,
      String filePath, String accountId, GitConfigDTO gitConfigDTO, LogCallback executionLogCallback,
      boolean closeLogStream) throws IOException {
    filePath = GitFetchTaskHelper.getCompleteFilePath(folderPath, filePath);
    List<String> filePaths = Collections.singletonList(filePath);
    gitFetchTaskHelper.printFileNames(executionLogCallback, filePaths, closeLogStream);
    return gitFetchTaskHelper.fetchFileFromRepo(gitStoreDelegateConfig, filePaths, accountId, gitConfigDTO);
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
